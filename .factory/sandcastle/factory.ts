// The factory, run on the maintainer's machine: `npm run factory` in this folder.
//
// For each ready ticket (up to the work-in-progress limit):
//   dispatch  .factory/dispatch.py picks the ticket and writes the task file (factory app token)
//   implement an agent in a Sandcastle Docker sandbox builds it on agent/issue-N
//   review    a fresh agent session in the same sandbox reviews it and commits fixes
//   publish   this script validates the branch, pushes it and opens the PR as the factory app
//
// Agents never hold a GitHub credential: only this script does, read from the macOS keychain.
// Each run gets its own Docker-in-Docker on a private network, so Testcontainers works inside the
// sandbox without the agent ever reaching the host's Docker. See docs/factory/how-it-works.md.

import { claudeCode, createSandbox } from "@ai-hero/sandcastle";
import { docker } from "@ai-hero/sandcastle/sandboxes/docker";
import { execFileSync } from "node:child_process";
import { createSign } from "node:crypto";
import { copyFileSync, existsSync, mkdirSync, readFileSync, rmSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

const repoRoot = execFileSync("git", ["rev-parse", "--show-toplevel"], { encoding: "utf8" }).trim();
const config = JSON.parse(readFileSync(join(repoRoot, ".factory/config.json"), "utf8"));
const REPO = "kylehoehns/rsvp-factory";
const IMAGE = "rsvp-factory-sandbox:25";
const DIND_IMAGE = "docker:29.9.2-dind";
const RUNS = ".factory/runs"; // gitignored; shared with the sandbox through the worktree
const PROTECTED = /^(\.github|\.factory|\.agents|\.claude|\.githooks|scripts)\//;

type Outcome = { outcome: "success" | "question" | "blocked"; summary: string; questions?: string[] };
type ReviewComment = { path?: string; line?: number; body: string };
type Review = { summary: string; comments: ReviewComment[] };

function run(command: string, args: string[], env: Record<string, string> = {}): string {
  return execFileSync(command, args, { cwd: repoRoot, encoding: "utf8", env: { ...process.env, ...env } }).trim();
}

function keychain(service: string): string {
  try {
    return run("security", ["find-generic-password", "-a", process.env.USER ?? "", "-s", service, "-w"]);
  } catch {
    throw new Error(`Missing keychain item '${service}'. Run .factory/sandcastle/setup-keychain.sh first.`);
  }
}

// --- GitHub, as the factory app ---

async function appToken(): Promise<string> {
  const appId = config.githubAppId;
  const privateKey = Buffer.from(keychain("rsvp-factory-app-key"), "base64").toString("utf8");
  const now = Math.floor(Date.now() / 1000);
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString("base64url");
  const unsigned = `${encode({ alg: "RS256", typ: "JWT" })}.${encode({ iat: now - 60, exp: now + 540, iss: appId })}`;
  const jwt = `${unsigned}.${createSign("RSA-SHA256").update(unsigned).sign(privateKey, "base64url")}`;

  const headers = { Authorization: `Bearer ${jwt}`, Accept: "application/vnd.github+json" };
  const installation = (await (await fetch(`https://api.github.com/repos/${REPO}/installation`, { headers })).json()) as {
    id: number;
  };
  const response = await fetch(`https://api.github.com/app/installations/${installation.id}/access_tokens`, {
    method: "POST",
    headers,
  });
  if (!response.ok) throw new Error(`Could not mint a factory app token: ${response.status} ${await response.text()}`);
  return ((await response.json()) as { token: string }).token;
}

function gh(token: string, ...args: string[]): string {
  return run("gh", args, { GH_TOKEN: token });
}

// --- One ticket ---

function dispatch(token: string): number | undefined {
  const output = run("python3", [".factory/dispatch.py"], {
    GITHUB_TOKEN: token,
    GITHUB_REPOSITORY: REPO,
    TASK_FILE: join(RUNS, "task.md"),
  });
  const match = output.match(/issue=(\d+)/);
  return match ? Number(match[1]) : undefined;
}

/** A private Docker for this run's Testcontainers, on a network only the sandbox shares. */
function startPrivateDocker(issue: number): { network: string; host: string; stop: () => void } {
  const network = `rsvp-factory-${issue}`;
  const host = `rsvp-dind-${issue}`;
  const stop = () => {
    execFileSync("docker", ["rm", "-f", host], { stdio: "ignore" });
    execFileSync("docker", ["network", "rm", network], { stdio: "ignore" });
  };
  try {
    stop();
  } catch {
    // nothing left over from an earlier run
  }
  run("docker", ["network", "create", network]);
  // Plain TCP is fine here: the daemon is only reachable on this run's private network.
  run("docker", ["run", "-d", "--privileged", "--name", host, "--network", network, "-e", "DOCKER_TLS_CERTDIR=",
    DIND_IMAGE, "--tls=false"]);
  for (let attempt = 0; ; attempt++) {
    try {
      execFileSync("docker", ["exec", host, "docker", "info"], { stdio: "ignore" });
      return { network, host, stop };
    } catch (error) {
      if (attempt > 60) throw error;
      execFileSync("sleep", ["1"]);
    }
  }
}

function readOutcome(worktree: string, role: string): Outcome {
  const file = join(worktree, RUNS, "outcome.json");
  if (!existsSync(file)) return { outcome: "blocked", summary: `The ${role} session ended without writing an outcome.` };
  const outcome = JSON.parse(readFileSync(file, "utf8")) as Outcome;
  rmSync(file);
  return outcome;
}

async function buildTicket(
  issue: number,
  claudeToken: string,
): Promise<Outcome & { prBody?: string; review?: Review }> {
  const branch = `agent/issue-${issue}`;
  run("git", ["fetch", "--quiet", "origin", "main"]);
  run("git", ["branch", "--force", branch, "origin/main"]);

  const privateDocker = startPrivateDocker(issue);
  const gradleCache = join(homedir(), ".cache/rsvp-factory/gradle");
  mkdirSync(gradleCache, { recursive: true });
  try {
    await using sandbox = await createSandbox({
      branch,
      cwd: repoRoot,
      sandbox: docker({
        imageName: IMAGE,
        network: privateDocker.network,
        mounts: [{ hostPath: gradleCache, sandboxPath: "/home/agent/.gradle" }],
        env: {
          // Sandcastle 0.12.0's createSandbox() ignores claudeCode({ env }) (it starts the container with
          // an empty agent env and sandbox.run() never adds it), so the token goes in the sandbox env.
          CLAUDE_CODE_OAUTH_TOKEN: claudeToken,
          RSVP_FACTORY: "1",
          DOCKER_HOST: `tcp://${privateDocker.host}:2375`,
          TESTCONTAINERS_HOST_OVERRIDE: privateDocker.host,
        },
      }),
    });

    // .factory/runs/ is gitignored, so a fresh worktree doesn't have it; the worktree is bind-mounted,
    // so writing here on the host is what the agent sees.
    mkdirSync(join(sandbox.worktreePath, RUNS), { recursive: true });
    copyFileSync(join(repoRoot, RUNS, "task.md"), join(sandbox.worktreePath, RUNS, "task.md"));

    const promptArgs = {
      TASK_FILE: join(RUNS, "task.md"),
      OUTCOME_FILE: join(RUNS, "outcome.json"),
      PR_BODY_FILE: join(RUNS, "pr-body.md"),
      REVIEW_FILE: join(RUNS, "review.json"),
      ISSUE: String(issue),
    };
    const agent = (model: string) => claudeCode(model);

    for (const role of ["implement", "review"] as const) {
      console.log(`#${issue}: ${role}…`);
      await sandbox.run({
        agent: agent(config[`${role}Model`]),
        promptFile: join(repoRoot, `.factory/prompts/${role}.md`),
        promptArgs,
        name: `${role} #${issue}`,
        logging: { type: "file", path: join(repoRoot, RUNS, "logs", `${role}-${issue}.log`) },
        signal: AbortSignal.timeout(config[`${role}TimeoutMinutes`] * 60_000),
      });
      const outcome = readOutcome(sandbox.worktreePath, role);
      if (outcome.outcome !== "success") return outcome;
    }

    const bodyFile = join(sandbox.worktreePath, RUNS, "pr-body.md");
    const reviewFile = join(sandbox.worktreePath, RUNS, "review.json");
    return {
      outcome: "success",
      summary: "",
      prBody: existsSync(bodyFile) ? readFileSync(bodyFile, "utf8") : undefined,
      review: existsSync(reviewFile) ? (JSON.parse(readFileSync(reviewFile, "utf8")) as Review) : undefined,
    };
  } finally {
    privateDocker.stop();
  }
}

function block(token: string, issue: number, reason: string) {
  gh(token, "issue", "edit", String(issue), "--repo", REPO, "--add-label", "agent:blocked", "--remove-label", "agent:in-progress");
  gh(token, "issue", "comment", String(issue), "--repo", REPO, "--body",
    `The factory stopped: ${reason}\n\nA human needs to look. Remove \`agent:blocked\` and re-apply \`ready-for-agent\` to retry.`);
  console.log(`#${issue}: blocked: ${reason}`);
}

/** Pocock's needs-info: leave the questions on the ticket and move on; answers arrive as comments. */
function ask(token: string, issue: number, outcome: Outcome) {
  const questions = (outcome.questions ?? []).map((q, i) => `${i + 1}. ${q}`).join("\n");
  gh(token, "issue", "comment", String(issue), "--repo", REPO, "--body",
    `The factory has questions before it can build this ticket.\n\n${questions || outcome.summary}\n\n` +
      "Answer in a comment, then re-apply `ready-for-agent`. The next run reads every comment on this ticket.");
  gh(token, "issue", "edit", String(issue), "--repo", REPO, "--add-label", "needs-info",
    "--remove-label", "ready-for-agent", "--remove-label", "agent:in-progress");
  console.log(`#${issue}: needs-info (${outcome.questions?.length ?? 0} question(s))`);
}

/** The factory reviewer's findings as a GitHub review from the app, inline where GitHub accepts the line. */
async function postReview(token: string, pr: number, review: Review) {
  const inline = review.comments.filter((c) => c.path && c.line);
  const general = review.comments.filter((c) => !(c.path && c.line));
  const asText = (c: ReviewComment) => `- ${c.path ? `\`${c.path}${c.line ? `:${c.line}` : ""}\`: ` : ""}${c.body}`;
  const post = (comments: ReviewComment[], extra: ReviewComment[]) =>
    fetch(`https://api.github.com/repos/${REPO}/pulls/${pr}/reviews`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json" },
      body: JSON.stringify({
        event: "COMMENT",
        body: [`**Factory review** (fresh session, \`code-review\` skill)`, review.summary, ...extra.map(asText)].join("\n\n"),
        comments: comments.map((c) => ({ path: c.path, line: c.line, side: "RIGHT", body: c.body })),
      }),
    });
  let response = await post(inline, general);
  // A line outside the diff makes GitHub reject the whole review; keep every finding by moving them all to the body.
  if (!response.ok) response = await post([], review.comments);
  if (!response.ok) console.log(`#${pr}: could not post the factory review: ${response.status} ${await response.text()}`);
}

async function publish(token: string, issue: number, prBody: string | undefined, review: Review | undefined) {
  const branch = `agent/issue-${issue}`;
  const changed = run("git", ["diff", "--name-only", `origin/main...${branch}`]).split("\n").filter(Boolean);
  if (changed.length === 0) return block(token, issue, "the agents reported success but made no commits.");
  const forbidden = changed.filter((path) => PROTECTED.test(path));
  if (forbidden.length) return block(token, issue, `the branch changes the factory's own controls: ${forbidden.join(", ")}.`);

  const auth = Buffer.from(`x-access-token:${token}`).toString("base64");
  run("git", ["-c", `http.https://github.com/.extraheader=AUTHORIZATION: basic ${auth}`, "push", "--force",
    `https://github.com/${REPO}.git`, `${branch}:${branch}`]);

  const title = gh(token, "issue", "view", String(issue), "--repo", REPO, "--json", "title", "--jq", ".title");
  const body = prBody?.includes(`Closes #${issue}`) ? prBody : `${prBody ?? ""}\n\nCloses #${issue}\n`;
  const url = gh(token, "pr", "create", "--repo", REPO, "--base", "main", "--head", branch, "--title", title, "--body", body);
  const pr = Number(url.split("/").pop());
  gh(token, "issue", "edit", String(issue), "--repo", REPO, "--remove-label", "agent:in-progress");
  if (review) await postReview(token, pr, review);

  // High-risk PRs get an independent second opinion (ADR 0003): a different model with a security brief.
  const tier = run("python3", [".github/scripts/risk_gate.py", "--local", `origin/main...${branch}`]);
  if (tier === "high") gh(token, "pr", "edit", String(pr), "--repo", REPO, "--add-label", "claude-review");
  console.log(`#${issue}: ${url} (${tier} risk)`);
}

// --- The shift ---

const claudeToken = keychain("rsvp-factory-claude-token");
// Fail before claiming a ticket: a wrong keychain item otherwise surfaces as "Not logged in" inside the sandbox.
if (!/^sk-ant-[A-Za-z0-9_-]+$/.test(claudeToken)) {
  throw new Error("The keychain item 'rsvp-factory-claude-token' doesn't look like a Claude token (sk-ant-...). " +
    "Re-run .factory/sandcastle/setup-keychain.sh with the output of 'claude setup-token'.");
}
for (;;) {
  const token = await appToken(); // short-lived; mint a fresh one per ticket
  const issue = dispatch(token);
  if (issue === undefined) {
    console.log("No ready ticket, or the work-in-progress limit is reached. Shift over.");
    break;
  }
  try {
    const result = await buildTicket(issue, claudeToken);
    const fresh = await appToken();
    if (result.outcome === "success") await publish(fresh, issue, result.prBody, result.review);
    else if (result.outcome === "question") ask(fresh, issue, result);
    else block(fresh, issue, result.summary);
  } catch (error) {
    block(await appToken(), issue, `the run failed: ${(error as Error).message}`);
  }
}
