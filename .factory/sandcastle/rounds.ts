// Fix rounds on open factory PRs, each capped (Stripe: at most two CI rounds, then a human):
//   ci      the `ci` check failed on the PR's head commit: a session gets the failed steps' log
//   update  the PR conflicts with main (another PR merged first): a session merges main in and resolves it

import { writeFileSync } from "node:fs";
import { join } from "node:path";
import { appToken, ciResult, failedLog, type FactoryPr, gh, openFactoryPrs, push, requestSecondOpinion, roundMarker, roundsTried } from "./github.ts";
import { pruneWorktrees, runSessions, type Session } from "./sandbox.ts";
import { BRANCH_PREFIX, config, forbiddenChanges, repoRoot, REPO, run, RUNS } from "./shared.ts";

type Hooks = { writeTask: (issue: number, token: string) => void; block: (token: string, target: number, reason: string) => void };
type Round = { kind: "ci" | "update"; name: string; session: Session; max: number; inputs: string[] };

const TASK = join(repoRoot, RUNS, "task.md");
const CI_LOG = join(repoRoot, RUNS, "ci-failure.log");

async function fixRound(pr: FactoryPr, round: Round, claudeToken: string, hooks: Hooks, token: string) {
  const tried = roundsTried(token, pr.number, round.kind);
  if (tried.includes(pr.headRefOid)) return; // this commit already had its round
  if (tried.length >= round.max) {
    hooks.block(token, pr.number, `${round.name} is still needed after ${tried.length} rounds.`);
    return;
  }
  const n = tried.length + 1;
  const issue = Number(pr.headRefName.slice(BRANCH_PREFIX.length));
  const branch = pr.headRefName;
  console.log(`PR #${pr.number}: ${round.name}, round ${n} of ${round.max}`);
  hooks.writeTask(issue, token);
  run("git", ["fetch", "--quiet", "origin", "main", branch]);
  pruneWorktrees();
  run("git", ["branch", "--force", branch, `origin/${branch}`]);

  const { outcome } = await runSessions(issue, branch, [round.session], round.inputs, claudeToken);
  const fresh = await appToken();
  const commits = run("git", ["rev-list", `origin/${branch}..${branch}`]);
  const forbidden = forbiddenChanges(branch);
  const label = `${round.name}, round ${n} of ${round.max}`;
  if (outcome.outcome !== "success" || !commits || forbidden.length) {
    const why = forbidden.length ? `the session touched the factory's own controls: ${forbidden.join(", ")}`
      : !commits ? `the session made no commits (${outcome.summary})` : outcome.summary;
    gh(fresh, "pr", "comment", String(pr.number), "--body", `${label}: ${why}\n\n${roundMarker(round.kind, pr.headRefOid)}`);
    hooks.block(fresh, pr.number, why);
    return;
  }
  push(fresh, branch); // fast-forward only
  gh(fresh, "pr", "comment", String(pr.number), "--body", `${label}: ${outcome.summary}\n\n${roundMarker(round.kind, pr.headRefOid)}`);
  requestSecondOpinion(fresh, pr.number); // the new commits haven't been read by anyone independent
  console.log(`PR #${pr.number}: pushed ${label}`);
}

export async function fixPrs(claudeToken: string, hooks: Hooks) {
  const token = await appToken();
  for (const pr of openFactoryPrs(token, BRANCH_PREFIX)) {
    if (pr.labels.some((l) => l.name === "agent:blocked")) continue;
    if (pr.mergeable === "CONFLICTING") {
      await fixRound(pr, { kind: "update", name: "Updating with main", max: config.maxUpdateRounds, inputs: [TASK],
        session: { role: "updateBranch", prompt: "update-branch.md" } }, claudeToken, hooks, token);
      continue; // CI on a conflicting branch says nothing useful; look again after the update
    }
    const ci = await ciResult(token, pr.headRefOid);
    if (!ci?.failed) continue;
    if (roundsTried(token, pr.number, "ci").includes(pr.headRefOid)) continue;
    writeFileSync(CI_LOG, failedLog(ci.runId));
    await fixRound(pr, { kind: "ci", name: "CI fix", max: config.maxCiRounds, inputs: [TASK, CI_LOG],
      session: { role: "fixCi", prompt: "fix-ci.md" } }, claudeToken,
      { ...hooks, block: (t, target, reason) => hooks.block(t, target, `${reason} Latest failure: https://github.com/${REPO}/actions/runs/${ci.runId}`) },
      token);
  }
}
