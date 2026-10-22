// Running agents: a Sandcastle Docker sandbox on a branch, plus a private Docker-in-Docker on its own network
// so Testcontainers works inside the sandbox without the agent ever reaching the host's Docker.

import { claudeCode, createSandbox } from "@ai-hero/sandcastle";
import { docker } from "@ai-hero/sandcastle/sandboxes/docker";
import { execFileSync } from "node:child_process";
import { appendFileSync, copyFileSync, existsSync, mkdirSync, readFileSync, rmSync } from "node:fs";
import { homedir } from "node:os";
import { basename, join } from "node:path";
import { config, type Outcome, repoRoot, run, RUNS } from "./shared.ts";

const IMAGE = "rsvp-factory-sandbox:25";
const BOT_EMAIL = "346099977+rsvp-factory-bot[bot]@users.noreply.github.com"; // GitHub links this to the app's bot user
const METRICS = join(repoRoot, RUNS, "metrics.jsonl"); // local only; scripts/factory_metrics.py reads it

/** Total tokens across every assistant message in the captured session transcripts. */
function sessionTokens(files: (string | undefined)[]) {
  const total = { input: 0, cacheWrite: 0, cacheRead: 0, output: 0, turns: 0 };
  for (const file of files) {
    if (!file || !existsSync(file)) continue;
    for (const line of readFileSync(file, "utf8").split("\n")) {
      const usage = line ? JSON.parse(line)?.message?.usage : undefined;
      if (!usage) continue;
      total.turns++;
      total.input += usage.input_tokens ?? 0;
      total.cacheWrite += usage.cache_creation_input_tokens ?? 0;
      total.cacheRead += usage.cache_read_input_tokens ?? 0;
      total.output += usage.output_tokens ?? 0;
    }
  }
  return total;
}
const DIND_IMAGE = "docker:29.9.2-dind";

function startPrivateDocker(issue: number): { network: string; host: string; stop: () => void } {
  const network = `rsvp-factory-${issue}`;
  const host = `rsvp-dind-${issue}`;
  const stop = () => {
    for (const args of [["rm", "-f", host], ["network", "rm", network]]) {
      try {
        execFileSync("docker", args, { stdio: "ignore" });
      } catch {
        // already gone
      }
    }
  };
  stop();
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

/** Drop worktrees Sandcastle left behind (e.g. after an interrupted run), so a branch can be reset. */
export function pruneWorktrees() {
  for (const line of run("git", ["worktree", "list", "--porcelain"]).split("\n")) {
    const path = line.startsWith("worktree ") ? line.slice(9) : "";
    if (path.includes("/.sandcastle/")) run("git", ["worktree", "remove", "--force", path]);
  }
  run("git", ["worktree", "prune"]);
}

export type Session = { role: "implement" | "review" | "fixCi" | "fixReview"; prompt: string };
export type SessionsResult = { outcome: Outcome; read: (file: string) => string | undefined };

/**
 * Runs each session in turn in one sandbox on `branch`, stopping at the first that doesn't succeed.
 * `inputs` are host files copied into the worktree's .factory/runs/ for the agents to read.
 */
export async function runSessions(issue: number, branch: string, sessions: Session[], inputs: string[],
  claudeToken: string): Promise<SessionsResult> {
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
          // Agents commit as the factory app, not as whoever's git identity the host repo has.
          GIT_AUTHOR_NAME: "rsvp-factory-bot[bot]",
          GIT_AUTHOR_EMAIL: BOT_EMAIL,
          GIT_COMMITTER_NAME: "rsvp-factory-bot[bot]",
          GIT_COMMITTER_EMAIL: BOT_EMAIL,
          DOCKER_HOST: `tcp://${privateDocker.host}:2375`,
          TESTCONTAINERS_HOST_OVERRIDE: privateDocker.host,
        },
      }),
    });

    // .factory/runs/ is gitignored, so a fresh worktree doesn't have it; the worktree is bind-mounted,
    // so writing here on the host is what the agent sees.
    const runsInWorktree = join(sandbox.worktreePath, RUNS);
    mkdirSync(runsInWorktree, { recursive: true });
    for (const input of inputs) copyFileSync(input, join(runsInWorktree, basename(input)));
    const read = (file: string) => {
      const path = join(runsInWorktree, file);
      return existsSync(path) ? readFileSync(path, "utf8") : undefined;
    };

    const promptArgs = {
      TASK_FILE: join(RUNS, "task.md"),
      CI_LOG_FILE: join(RUNS, "ci-failure.log"),
      FINDINGS_FILE: join(RUNS, "findings.json"),
      RESOLUTIONS_FILE: join(RUNS, "resolutions.json"),
      OUTCOME_FILE: join(RUNS, "outcome.json"),
      PR_BODY_FILE: join(RUNS, "pr-body.md"),
      REVIEW_FILE: join(RUNS, "review.json"),
      ISSUE: String(issue),
    };
    for (const session of sessions) {
      console.log(`#${issue}: ${session.role}…`);
      const started = Date.now();
      const result = await sandbox.run({
        agent: claudeCode(config[`${session.role}Model`]),
        promptFile: join(repoRoot, ".factory/prompts", session.prompt),
        promptArgs,
        name: `${session.role} #${issue}`,
        logging: { type: "file", path: join(repoRoot, RUNS, "logs", `${session.role}-${issue}.log`) },
        signal: AbortSignal.timeout(config[`${session.role}TimeoutMinutes`] * 60_000),
      });
      const outcomeText = read("outcome.json");
      rmSync(join(runsInWorktree, "outcome.json"), { force: true });
      const outcome: Outcome = outcomeText
        ? JSON.parse(outcomeText)
        : { outcome: "blocked", summary: `The ${session.role} session ended without writing an outcome.` };
      appendFileSync(METRICS, JSON.stringify({
        at: new Date().toISOString(), issue, role: session.role, outcome: outcome.outcome, minutes: (Date.now() - started) / 60_000,
        commits: result.commits.length, tokens: sessionTokens(result.iterations.map((i) => i.sessionFilePath)),
      }) + "\n");
      if (outcome.outcome !== "success") return { outcome, read };
    }
    // Read the results before the sandbox (and its worktree) is disposed.
    const snapshot = Object.fromEntries(["pr-body.md", "review.json", "resolutions.json"].map((f) => [f, read(f)]));
    return { outcome: { outcome: "success", summary: "" }, read: (file) => snapshot[file] };
  } finally {
    privateDocker.stop();
  }
}
