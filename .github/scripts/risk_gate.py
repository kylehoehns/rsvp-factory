"""Risk gate: decide how much review a PR needs, then say whether it has had it.

Run by .github/workflows/risk-gate.yml. Never checks out or executes PR code: it reads the PR's
metadata and file list through the GitHub API and the policy from the base branch.

Shadow mode labels the tier and always passes, so the maintainer can compare the gate's call with
their own before it starts blocking anything.
"""

from __future__ import annotations

import fnmatch
import json
import os
import re
import sys
import tomllib
import urllib.request
from dataclasses import dataclass, field

TIERS = ["low", "medium", "high"]


@dataclass(frozen=True)
class ChangedFile:
    path: str
    status: str  # added, modified, removed, renamed, copied, changed
    changes: int
    previous_path: str | None = None


@dataclass
class Decision:
    tier: str = "low"
    reasons: list[str] = field(default_factory=list)

    def raise_to(self, tier: str, reason: str) -> None:
        """Tiers only ever go up: no rule, and no classifier, can lower a floor set by another."""
        if TIERS.index(tier) > TIERS.index(self.tier):
            self.tier = tier
        self.reasons.append(f"{tier}: {reason}")


def matches(path: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatch(path, pattern) for pattern in patterns)


def floor_tier(
    files: list[ChangedFile], policy: dict, author: str, author_is_trusted: bool, body: str
) -> Decision:
    """The deterministic part of the tier: paths, size, author and the PR's own Merge Danger."""
    decision = Decision()
    low = policy["low"]
    paths = [p for f in files for p in (f.path, f.previous_path) if p]

    for path in paths:
        if matches(path, policy["high"]["paths"]):
            decision.raise_to("high", f"{path} is a high-risk path")

    for f in files:
        if f.status != "added" and matches(f.previous_path or f.path, policy["medium"]["existing_paths"]):
            decision.raise_to("medium", f"{f.path} changes something already in the acceptance suite")

    outside_low = [p for p in paths if not matches(p, low["paths"]) or matches(p, low["exclude"])]
    if outside_low:
        decision.raise_to("medium", f"{len(outside_low)} file(s) outside the low-risk paths, e.g. {outside_low[0]}")

    changed_lines = sum(f.changes for f in files)
    if changed_lines > low["max_changed_lines"]:
        decision.raise_to("medium", f"{changed_lines} changed lines (low allows {low['max_changed_lines']})")

    if not author_is_trusted:
        decision.raise_to("medium", f"author {author} is not a maintainer or the factory")

    if re.search(r"\*\*Door:\*\*\s*one-way", body or "", re.IGNORECASE):
        decision.raise_to("medium", "the PR's Merge Danger says one-way door")

    if not decision.reasons:
        decision.reasons.append("low: small change to low-risk paths only")
    return decision


# --- GitHub plumbing below; the decision above is what the tests cover. ---


def api(method: str, path: str, body: dict | None = None) -> object:
    request = urllib.request.Request(
        f"https://api.github.com/repos/{os.environ['GITHUB_REPOSITORY']}{path}",
        method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={
            "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    with urllib.request.urlopen(request) as response:
        text = response.read().decode()
        return json.loads(text) if text else None


def changed_files(pr_number: int) -> list[ChangedFile]:
    files, page = [], 1
    while True:
        batch = api("GET", f"/pulls/{pr_number}/files?per_page=100&page={page}")
        files += [
            ChangedFile(f["filename"], f["status"], f["changes"], f.get("previous_filename"))
            for f in batch  # type: ignore[union-attr]
        ]
        if len(batch) < 100:  # type: ignore[arg-type]
            return files
        page += 1


def set_tier_label(pr_number: int, tier: str, current: list[str]) -> None:
    for label in current:
        if label.startswith("risk:") and label != f"risk:{tier}":
            api("DELETE", f"/issues/{pr_number}/labels/{label.replace(':', '%3A')}")
    if f"risk:{tier}" not in current:
        api("POST", f"/issues/{pr_number}/labels", {"labels": [f"risk:{tier}"]})


def post_status(sha: str, state: str, description: str) -> None:
    api(
        "POST",
        f"/statuses/{sha}",
        {"state": state, "context": "risk-gate", "description": description[:140]},
    )


def main() -> int:
    pr_number = int(os.environ["PR_NUMBER"])
    mode = os.environ.get("RISK_GATE_MODE", "shadow")
    with open(os.environ.get("RISK_POLICY", ".github/risk-policy.toml"), "rb") as policy_file:
        policy = tomllib.load(policy_file)

    pr = api("GET", f"/pulls/{pr_number}")
    author = pr["user"]["login"]  # type: ignore[index]
    trusted = author in policy["maintainers"] or author == os.environ.get("FACTORY_BOT", "")
    decision = floor_tier(changed_files(pr_number), policy, author, trusted, pr["body"] or "")  # type: ignore[index]

    set_tier_label(pr_number, decision.tier, [label["name"] for label in pr["labels"]])  # type: ignore[index]

    summary = "\n".join(f"- {reason}" for reason in decision.reasons)
    with open(os.environ.get("GITHUB_STEP_SUMMARY", os.devnull), "a") as out:
        out.write(f"## Risk tier: {decision.tier} ({mode} mode)\n\n{summary}\n")
    print(f"tier={decision.tier}\n{summary}")

    if mode == "shadow":
        post_status(pr["head"]["sha"], "success", f"shadow: would be {decision.tier}")  # type: ignore[index]
    return 0


if __name__ == "__main__":
    sys.exit(main())
