"""Pick the next ticket for the factory, deterministically. No agent decides what to work on.

A ticket is ready when it is open, labelled ready-for-agent by a maintainer, is a leaf (no
sub-issues, not a spec), isn't already claimed or blocked, has no open factory PR, and every issue
blocking it is closed.
The work-in-progress limit counts claimed tickets plus open factory PRs (branches agent/issue-N). Lowest number wins, which
is dependency order because /to-tickets publishes blockers first.

Writes the chosen ticket, its comments and its parent spec to the task file as data, and prints
`issue=<n>` for the workflow (nothing when there is no work).
"""

from __future__ import annotations

import json
import os
import sys
import tomllib
import urllib.request
from dataclasses import dataclass

CLAIMED = "agent:in-progress"
BLOCKED = "agent:blocked"
TRIGGER = "ready-for-agent"
BRANCH_PREFIX = "agent/issue-"


@dataclass(frozen=True)
class Ticket:
    number: int
    labels: frozenset[str]
    open_blockers: int
    sub_issues: int
    labelled_by: str | None
    has_open_pr: bool = False


def pick(tickets: list[Ticket], work_in_progress: int, wip_limit: int, maintainers: set[str]) -> Ticket | None:
    if work_in_progress >= wip_limit:
        return None
    ready = [
        t
        for t in tickets
        if TRIGGER in t.labels
        and not t.labels & {CLAIMED, BLOCKED, "spec"}
        and t.sub_issues == 0
        and not t.has_open_pr  # its PR is waiting for review; building it again would overwrite it
        and t.open_blockers == 0
        and t.labelled_by in maintainers
    ]
    return min(ready, key=lambda t: t.number, default=None)


# --- GitHub plumbing ---


def api(path: str, method: str = "GET", body: dict | None = None) -> object:
    url = path if path.startswith("https://") else f"https://api.github.com/repos/{os.environ['GITHUB_REPOSITORY']}{path}"
    request = urllib.request.Request(
        url,
        method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={
            "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    try:
        with urllib.request.urlopen(request) as response:
            text = response.read().decode()
            return json.loads(text) if text else None
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise


def labelled_by(number: int) -> str | None:
    events = api(f"/issues/{number}/events?per_page=100") or []
    labelled = [e for e in events if e["event"] == "labeled" and e["label"]["name"] == TRIGGER]  # type: ignore[index]
    return labelled[-1]["actor"]["login"] if labelled else None  # type: ignore[index]


def open_factory_pr_tickets() -> set[int]:
    pulls = api("/pulls?state=open&per_page=100") or []
    return {int(p["head"]["ref"][len(BRANCH_PREFIX):]) for p in pulls if p["head"]["ref"].startswith(BRANCH_PREFIX)}  # type: ignore[index]


def load_tickets() -> list[Ticket]:
    issues = api(f"/issues?state=open&labels={TRIGGER}&per_page=100") or []
    with_prs = open_factory_pr_tickets()
    return [
        Ticket(
            number=i["number"],
            labels=frozenset(label["name"] for label in i["labels"]),
            open_blockers=(i.get("issue_dependencies_summary") or {}).get("blocked_by", 0),
            sub_issues=(i.get("sub_issues_summary") or {}).get("total", 0),
            labelled_by=labelled_by(i["number"]),
            has_open_pr=i["number"] in with_prs,
        )
        for i in issues  # type: ignore[union-attr]
        if "pull_request" not in i
    ]


def work_in_progress() -> int:
    """Claimed tickets still being built, plus factory PRs not yet merged or closed."""
    claimed = api(f"/issues?state=open&labels={CLAIMED}&per_page=100") or []
    pulls = api("/pulls?state=open&per_page=100") or []
    open_factory_prs = [p for p in pulls if p["head"]["ref"].startswith(BRANCH_PREFIX)]  # type: ignore[index]
    return len(claimed) + len(open_factory_prs)  # type: ignore[arg-type]


def write_task(number: int, path: str) -> None:
    issue = api(f"/issues/{number}")
    comments = api(f"/issues/{number}/comments?per_page=100") or []
    parent = api(f"/issues/{number}/parent")
    parts = [f"# Ticket #{number}: {issue['title']}\n\n{issue['body'] or ''}"]  # type: ignore[index]
    parts += [f"## Comment by {c['user']['login']}\n\n{c['body']}" for c in comments]  # type: ignore[index]
    if parent:
        parts.append(f"# Parent spec #{parent['number']}: {parent['title']}\n\n{parent['body'] or ''}")  # type: ignore[index]
    with open(path, "w") as task:
        task.write("\n\n---\n\n".join(parts) + "\n")


def main() -> int:
    with open(".github/risk-policy.toml", "rb") as policy_file:
        maintainers = set(tomllib.load(policy_file)["maintainers"])
    with open(".factory/config.json") as config_file:
        wip_limit = json.load(config_file)["wipLimit"]

    ticket = pick(load_tickets(), work_in_progress(), wip_limit, maintainers)
    if ticket is None:
        print("No ready ticket, or the work-in-progress limit is reached.", file=sys.stderr)
        return 0

    api(f"/issues/{ticket.number}/labels", "POST", {"labels": [CLAIMED]})
    write_task(ticket.number, os.environ.get("TASK_FILE", "task.md"))
    print(f"issue={ticket.number}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
