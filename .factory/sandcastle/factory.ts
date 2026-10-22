// The factory, run on the maintainer's machine: `npm run factory` in this folder. It keeps running and,
// every `pollSeconds`, does four things (`npm run factory -- --once` does them once and exits):
//
//   1. fix CI   a factory PR whose `ci` check failed gets a fix session on its branch with the failure log,
//               at most `maxCiRounds` times; after that it's labelled agent:blocked for the maintainer
//   2. review   a factory PR's second opinion is triaged: blockers get a fix round (at most `maxReviewRounds`),
//               one-way doors are labelled decision-needed, follow-ups are listed on the PR (findings.ts)
//   +  auto-merge  low-risk PRs with everything green and resolved: commented in shadow mode, merged when on
//   3. follow-ups  when a factory PR merges, its follow-ups are filed as needs-triage tickets
//   4. tickets  each ready ticket (up to the work-in-progress limit) is dispatched, implemented and reviewed
//               in a sandbox, then published as a PR by the factory app
//
// Agents never hold a GitHub credential: github.ts does, with the app key from the macOS keychain.
// See docs/factory/how-it-works.md.

import { writeFileSync } from "node:fs";
import { join } from "node:path";
import { api, appToken, ciResult, ciRoundsTried, failedLog, gh, openFactoryPrs, push, roundMarker } from "./github.ts";
import { autoMerge } from "./automerge.ts";
import { addFollowUps, fileFollowUps, type Finding, flagDecisions, settleFindings, triageSecondOpinion } from "./findings.ts";
import { pruneWorktrees, runSessions } from "./sandbox.ts";
import { BRANCH_PREFIX, config, forbiddenChanges, keychain, type Outcome, repoRoot, REPO, run, RUNS } from "./shared.ts";

type Review = { summary: string; findings: Finding[] };

const TASK = join(repoRoot, RUNS, "task.md");
const CI_LOG = join(repoRoot, RUNS, "ci-failure.log");

function writeTask(issue: number, token: string) {
  run("python3", ["-c", `import sys; sys.path.insert(0, '.factory'); import dispatch; dispatch.write_task(${issue}, '${TASK}')`],
    { GITHUB_TOKEN: token, GITHUB_REPOSITORY: REPO });
}

function block(token: string, target: number, reason: string) {
  gh(token, "issue", "edit", String(target), "--add-label", "agent:blocked");
  try {
    gh(token, "issue", "edit", String(target), "--remove-label", "agent:in-progress");
  } catch {
    // PRs never carry it
  }
  gh(token, "issue", "comment", String(target), "--body",
    `The factory stopped: ${reason}\n\nA human needs to look. Remove \`agent:blocked\` to let the factory try again.`);
  console.log(`#${target}: blocked: ${reason}`);
}

/** Pocock's needs-info: leave the questions on the ticket and move on; answers arrive as comments. */
function ask(token: string, issue: number, outcome: Outcome) {
  const questions = (outcome.questions ?? []).map((q, i) => `${i + 1}. ${q}`).join("\n");
  gh(token, "issue", "comment", String(issue), "--body",
    `The factory has questions before it can build this ticket.\n\n${questions || outcome.summary}\n\n` +
      "Answer in a comment, then re-apply `ready-for-agent`. The next run reads every comment on this ticket.");
  gh(token, "issue", "edit", String(issue), "--add-label", "needs-info", "--remove-label", "ready-for-agent",
    "--remove-label", "agent:in-progress");
  console.log(`#${issue}: needs-info (${outcome.questions?.length ?? 0} question(s))`);
}

// --- 1. Fixing red CI on factory PRs ---

async function fixRedCi(claudeToken: string) {
  const token = await appToken();
  for (const pr of openFactoryPrs(token, BRANCH_PREFIX)) {
    if (pr.labels.some((l) => l.name === "agent:blocked")) continue;
    const ci = await ciResult(token, pr.headRefOid);
    if (!ci?.failed) continue;
    const tried = ciRoundsTried(token, pr.number);
    if (tried.includes(pr.headRefOid)) continue; // this commit already had its round
    if (tried.length >= config.maxCiRounds) {
      block(token, pr.number, `CI is still failing after ${tried.length} fix rounds. Latest failure: ` +
        `https://github.com/${REPO}/actions/runs/${ci.runId}`);
      continue;
    }

    const round = tried.length + 1;
    const issue = Number(pr.headRefName.slice(BRANCH_PREFIX.length));
    const branch = pr.headRefName;
    console.log(`PR #${pr.number}: CI failed, fix round ${round} of ${config.maxCiRounds}`);
    writeTask(issue, token);
    writeFileSync(CI_LOG, failedLog(ci.runId));
    run("git", ["fetch", "--quiet", "origin", "main", branch]);
    pruneWorktrees();
    run("git", ["branch", "--force", branch, `origin/${branch}`]);

    const { outcome } = await runSessions(issue, branch, [{ role: "fixCi", prompt: "fix-ci.md" }], [TASK, CI_LOG],
      claudeToken);
    const fresh = await appToken();
    const commits = run("git", ["rev-list", `origin/${branch}..${branch}`]);
    const forbidden = forbiddenChanges(branch);
    if (outcome.outcome !== "success" || !commits || forbidden.length) {
      const why = forbidden.length ? `the fix touched the factory's own controls: ${forbidden.join(", ")}`
        : !commits ? `the fix session made no commits (${outcome.summary})` : outcome.summary;
      gh(fresh, "pr", "comment", String(pr.number), "--body", `CI round ${round} of ${config.maxCiRounds}: ${why}\n\n${roundMarker(pr.headRefOid)}`);
      block(fresh, pr.number, why);
      continue;
    }
    push(fresh, branch); // fast-forward only
    gh(fresh, "pr", "comment", String(pr.number), "--body",
      `CI round ${round} of ${config.maxCiRounds}: ${outcome.summary}\n\n${roundMarker(pr.headRefOid)}`);
    console.log(`PR #${pr.number}: pushed CI fix round ${round}`);
  }
}

// --- 2. Second opinions on factory PRs ---

async function reviewFindings(claudeToken: string) {
  const token = await appToken();
  for (const pr of openFactoryPrs(token, BRANCH_PREFIX)) {
    if (pr.labels.some((l) => l.name === "agent:blocked")) continue;
    const issue = Number(pr.headRefName.slice(BRANCH_PREFIX.length));
    await triageSecondOpinion(token, pr.number, pr.headRefName, issue, claudeToken, appToken,
      (target, reason) => block(token, target, reason), () => writeTask(issue, token));
  }
}

// --- Auto-merge for low-risk PRs (off / shadow / on, ADR 0006) ---

async function lowRiskMerges() {
  const token = await appToken();
  await autoMerge(token, openFactoryPrs(token, BRANCH_PREFIX));
}

// --- 3. Follow-ups from merged PRs ---

async function followUps() {
  fileFollowUps(await appToken(), BRANCH_PREFIX);
}

// --- 4. Building tickets ---

function dispatch(token: string): number | undefined {
  const output = run("python3", [".factory/dispatch.py"], { GITHUB_TOKEN: token, GITHUB_REPOSITORY: REPO, TASK_FILE: TASK });
  const match = output.match(/issue=(\d+)/);
  return match ? Number(match[1]) : undefined;
}

/** The factory reviewer's findings as a GitHub review from the app, inline where GitHub accepts the line. */
async function postReview(token: string, pr: number, review: Review) {
  const text = (f: Finding) => `[${f.kind}] **${f.title}**: ${f.body}`;
  const asLine = (f: Finding) => `- ${f.path ? `\`${f.path}${f.line ? `:${f.line}` : ""}\`: ` : ""}${text(f)}`;
  const post = (inline: Finding[], inBody: Finding[]) =>
    api(token, `/pulls/${pr}/reviews`, {
      method: "POST",
      body: JSON.stringify({
        event: "COMMENT",
        body: ["**Factory review** (fresh session, `code-review` skill)", review.summary, ...inBody.map(asLine)].join("\n\n"),
        comments: inline.map((f) => ({ path: f.path, line: f.line, side: "RIGHT", body: text(f) })),
      }),
    });
  const inline = review.findings.filter((f) => f.path && f.line);
  try {
    await post(inline, review.findings.filter((f) => !inline.includes(f)));
  } catch {
    // A line outside the diff makes GitHub reject the whole review; keep every finding in the body instead.
    await post([], review.findings).catch((e) => console.log(`#${pr}: could not post the factory review: ${e}`));
  }
  addFollowUps(token, pr, review.findings.filter((f) => f.kind === "follow-up"));
  flagDecisions(token, pr, review.findings.filter((f) => f.kind === "one-way-door"));
  // What the reviewer already fixed, or deferred to a ticket, needs no one's attention: resolve those threads.
  await settleFindings(token, pr, review.findings.filter((f) => f.kind === "fixed" || f.kind === "follow-up"));
}

async function publish(token: string, issue: number, prBody: string | undefined, review: Review | undefined) {
  const branch = `${BRANCH_PREFIX}${issue}`;
  if (!run("git", ["rev-list", `origin/main..${branch}`])) return block(token, issue, "the agents reported success but made no commits.");
  const forbidden = forbiddenChanges(branch);
  if (forbidden.length) return block(token, issue, `the branch changes the factory's own controls: ${forbidden.join(", ")}.`);

  push(token, branch, true); // a fresh branch from main; replaces any earlier attempt
  const title = gh(token, "issue", "view", String(issue), "--json", "title", "--jq", ".title");
  const body = prBody?.includes(`Closes #${issue}`) ? prBody : `${prBody ?? ""}\n\nCloses #${issue}\n`;
  const url = gh(token, "pr", "create", "--base", "main", "--head", branch, "--title", title, "--body", body);
  const pr = Number(url.split("/").pop());
  gh(token, "issue", "edit", String(issue), "--remove-label", "agent:in-progress");
  if (review) await postReview(token, pr, review);

  // High-risk PRs get an independent second opinion (ADR 0003).
  const tier = run("python3", [".github/scripts/risk_gate.py", "--local", `origin/main...${branch}`]);
  if (tier === "high") gh(token, "pr", "edit", String(pr), "--add-label", "claude-review");
  console.log(`#${issue}: ${url} (${tier} risk)`);
}

async function buildTickets(claudeToken: string) {
  for (;;) {
    const issue = dispatch(await appToken());
    if (issue === undefined) return;
    const branch = `${BRANCH_PREFIX}${issue}`;
    try {
      run("git", ["fetch", "--quiet", "origin", "main"]);
      pruneWorktrees();
      run("git", ["branch", "--force", branch, "origin/main"]);
      const { outcome, read } = await runSessions(issue, branch,
        [{ role: "implement", prompt: "implement.md" }, { role: "review", prompt: "review.md" }], [TASK], claudeToken);
      const token = await appToken();
      if (outcome.outcome === "success") {
        const review = read("review.json");
        await publish(token, issue, read("pr-body.md"), review ? (JSON.parse(review) as Review) : undefined);
      } else if (outcome.outcome === "question") ask(token, issue, outcome);
      else block(token, issue, outcome.summary);
    } catch (error) {
      block(await appToken(), issue, `the run failed: ${(error as Error).message}`);
    }
  }
}

// --- The loop ---

const claudeToken = keychain("rsvp-factory-claude-token");
// Fail before touching GitHub: a wrong keychain item otherwise surfaces as "Not logged in" inside the sandbox.
if (!/^sk-ant-[A-Za-z0-9_-]+$/.test(claudeToken)) {
  throw new Error("The keychain item 'rsvp-factory-claude-token' doesn't look like a Claude token (sk-ant-...). " +
    "Re-run .factory/sandcastle/setup-keychain.sh --token with the output of 'claude setup-token'.");
}
const once = process.argv.includes("--once");
console.log(once ? "Factory: one pass." : `Factory: watching every ${config.pollSeconds}s. Ctrl+C to stop.`);
for (;;) {
  for (const step of [fixRedCi, reviewFindings, lowRiskMerges, followUps, buildTickets]) {
    try {
      await step(claudeToken);
    } catch (error) {
      console.log(`${step.name}: ${(error as Error).message}`); // keep watching; the next pass retries
    }
  }
  if (once) break;
  await new Promise((resolve) => setTimeout(resolve, config.pollSeconds * 1000));
}
