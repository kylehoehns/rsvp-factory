// Merging factory PRs (docs/adr/0006-auto-merge-low-risk-in-stages.md).
// Low tier, no human, in stages:
//   off     nothing happens (approved PRs aren't merged either: `off` stops all factory merges)
//   shadow  the factory comments "would auto-merge" once per head commit; the maintainer still merges
//   on      the factory merges, unless the stop rule has tripped
// Medium and high tier: once a maintainer has approved the head commit and everything is green, the factory merges
// it in `shadow` and `on` alike. The decision was the maintainer's; the factory only clicks the button.

import { api, ciResult, type FactoryPr, gh, reviewThreads } from "./github.ts";
import { config, run } from "./shared.ts";

const MARK = (sha: string) => `<!-- factory-auto-merge sha=${sha} -->`;
const AUTO_MERGED = "Auto-merged by the factory";

/** The stop rule: a revert of any auto-merged PR pauses auto-merge until the maintainer turns it back on. */
function stopRuleTripped(token: string): string | undefined {
  const reverts = run("git", ["log", "origin/main", "--since=30 days ago", "--grep=^Revert", "--format=%s%n%b"]);
  for (const pr of new Set([...reverts.matchAll(/#(\d+)/g)].map((m) => m[1]))) {
    const comments = gh(token, "pr", "view", pr, "--json", "comments", "--jq", "[.comments[].body] | join(\"\\n\")");
    if (comments.includes(AUTO_MERGED)) return `#${pr} was auto-merged and later reverted`;
  }
  return undefined;
}

/** Maintainers, from the risk policy on main: a PR can't add itself to the list. */
function maintainers(): string[] {
  const policy = run("git", ["show", "origin/main:.github/risk-policy.toml"]);
  return [...(policy.match(/^maintainers\s*=\s*\[(.*)\]/m)?.[1] ?? "").matchAll(/"([^"]+)"/g)].map((m) => m[1]);
}

type Review = { user: { login: string }; state: string; commit_id: string };

/** The maintainer who approved this exact commit, if one did and hasn't since asked for changes. A push after the
 *  approval moves the head, so it needs approving again. */
async function approvedBy(token: string, pr: FactoryPr): Promise<string | undefined> {
  const people = maintainers();
  const reviews = (await api<Review[]>(token, `/pulls/${pr.number}/reviews?per_page=100`))
    .filter((r) => people.includes(r.user.login) && r.state !== "COMMENTED");
  const latest = reviews.at(-1);
  return latest?.state === "APPROVED" && latest.commit_id === pr.headRefOid ? latest.user.login : undefined;
}

/** Why this PR can't merge yet, or undefined if it can. `approver` is set for a medium or high PR a maintainer approved. */
async function qualifies(token: string, pr: FactoryPr, approver: string | undefined): Promise<string | undefined> {
  const labels = new Set(pr.labels.map((l) => l.name));
  if (!labels.has("risk:low") && !approver) return "not low tier, and no maintainer approval of the head commit";
  for (const label of ["decision-needed", "agent:blocked"]) if (labels.has(label)) return `labelled ${label}`;
  const ci = await ciResult(token, pr.headRefOid);
  if (!ci || ci.failed) return "ci not green";
  const { statuses } = await api<{ statuses: { context: string; state: string }[] }>(token, `/commits/${pr.headRefOid}/status`);
  if (statuses.find((s) => s.context === "risk-gate")?.state !== "success") return "risk-gate not green";
  // High tier also needs the independent second opinion to have passed this commit (ADR 0003).
  if (labels.has("risk:high") && statuses.find((s) => s.context === "second-opinion")?.state !== "success") {
    return "second opinion hasn't passed the head commit";
  }
  if ((await reviewThreads(token, pr.number)).some((t) => !t.isResolved)) return "unresolved review threads";
  return undefined;
}

export async function autoMerge(token: string, prs: FactoryPr[]) {
  const mode = config.autoMerge as "off" | "shadow" | "on";
  if (mode === "off") return;
  const stopped = mode === "on" ? stopRuleTripped(token) : undefined;
  for (const pr of prs) {
    const approver = pr.labels.some((l) => l.name === "risk:low") ? undefined : await approvedBy(token, pr);
    if ((await qualifies(token, pr, approver)) !== undefined) continue;
    const comments = gh(token, "pr", "view", String(pr.number), "--json", "comments", "--jq", "[.comments[].body] | join(\"\\n\")");
    if (comments.includes(MARK(pr.headRefOid))) continue;
    if (approver) {
      // Not an auto-merge: a revert of this PR doesn't trip the stop rule, because a person approved it.
      gh(token, "pr", "comment", String(pr.number), "--body", `Merged by the factory: ${approver} approved ${pr.headRefOid.slice(0, 7)} and all checks are green.\n\n${MARK(pr.headRefOid)}`);
      await api(token, `/pulls/${pr.number}/merge`, { method: "PUT", body: JSON.stringify({ merge_method: "squash", sha: pr.headRefOid }) });
      console.log(`PR #${pr.number}: merged after ${approver}'s approval`);
      continue;
    }
    if (mode === "shadow" || stopped) {
      const why = stopped ? `Auto-merge is paused by the stop rule: ${stopped}.` : "Shadow mode: the maintainer merges.";
      gh(token, "pr", "comment", String(pr.number), "--body", `Would auto-merge: low tier, all checks green, nothing left open. ${why}\n\n${MARK(pr.headRefOid)}`);
      console.log(`PR #${pr.number}: would auto-merge (${stopped ? "paused" : "shadow"})`);
      continue;
    }
    gh(token, "pr", "comment", String(pr.number), "--body", `${AUTO_MERGED}: low tier, all checks green, nothing left open.\n\n${MARK(pr.headRefOid)}`);
    await api(token, `/pulls/${pr.number}/merge`, { method: "PUT", body: JSON.stringify({ merge_method: "squash", sha: pr.headRefOid }) });
    console.log(`PR #${pr.number}: auto-merged`);
  }
}
