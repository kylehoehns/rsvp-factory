// Auto-merge for low-risk factory PRs, in stages (docs/adr/0006-auto-merge-low-risk-in-stages.md).
//   off     nothing happens
//   shadow  the factory comments "would auto-merge" once per head commit; the maintainer still merges
//   on      the factory merges, unless the stop rule has tripped

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

async function qualifies(token: string, pr: FactoryPr): Promise<string | undefined> {
  const labels = new Set(pr.labels.map((l) => l.name));
  if (!labels.has("risk:low")) return "not low tier";
  for (const label of ["decision-needed", "agent:blocked", "claude-review"]) if (labels.has(label)) return `labelled ${label}`;
  const ci = await ciResult(token, pr.headRefOid);
  if (!ci || ci.failed) return "ci not green";
  const { statuses } = await api<{ statuses: { context: string; state: string }[] }>(token, `/commits/${pr.headRefOid}/status`);
  if (statuses.find((s) => s.context === "risk-gate")?.state !== "success") return "risk-gate not green";
  if ((await reviewThreads(token, pr.number)).some((t) => !t.isResolved)) return "unresolved review threads";
  return undefined;
}

export async function autoMerge(token: string, prs: FactoryPr[]) {
  const mode = config.autoMerge as "off" | "shadow" | "on";
  if (mode === "off") return;
  const stopped = mode === "on" ? stopRuleTripped(token) : undefined;
  for (const pr of prs) {
    if ((await qualifies(token, pr)) !== undefined) continue;
    const comments = gh(token, "pr", "view", String(pr.number), "--json", "comments", "--jq", "[.comments[].body] | join(\"\\n\")");
    if (comments.includes(MARK(pr.headRefOid))) continue;
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
