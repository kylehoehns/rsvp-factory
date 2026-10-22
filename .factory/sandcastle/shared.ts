// Things every part of the factory needs: where the repo is, its config, and running a command.

import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { join } from "node:path";

export const repoRoot = execFileSync("git", ["rev-parse", "--show-toplevel"], { encoding: "utf8" }).trim();
export const config = JSON.parse(readFileSync(join(repoRoot, ".factory/config.json"), "utf8"));
export const REPO = "kylehoehns/rsvp-factory";
export const RUNS = ".factory/runs"; // gitignored; shared with the sandbox through the worktree
export const BRANCH_PREFIX = "agent/issue-";
/** The factory's own controls. Agents may never change these; the hooks block it and publishing re-checks. */
export const PROTECTED = /^(\.github|\.factory|\.agents|\.claude|\.githooks|scripts)\//;

export type Outcome = { outcome: "success" | "question" | "blocked"; summary: string; questions?: string[] };

export function run(command: string, args: string[], env: Record<string, string> = {}): string {
  return execFileSync(command, args, { cwd: repoRoot, encoding: "utf8", env: { ...process.env, ...env } }).trim();
}

export function keychain(service: string): string {
  try {
    return run("security", ["find-generic-password", "-a", process.env.USER ?? "", "-s", service, "-w"]);
  } catch {
    throw new Error(`Missing keychain item '${service}'. Run .factory/sandcastle/setup-keychain.sh first.`);
  }
}

/** Files changed on a branch since it left main that touch the factory's own controls. */
export function forbiddenChanges(branch: string): string[] {
  return run("git", ["diff", "--name-only", `origin/main...${branch}`])
    .split("\n")
    .filter((path) => PROTECTED.test(path));
}
