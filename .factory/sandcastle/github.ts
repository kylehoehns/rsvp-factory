// GitHub, as the factory's app. Only this file holds a GitHub credential; agents never do.

import { createSign } from "node:crypto";
import { config, keychain, REPO, run } from "./shared.ts";

export async function appToken(): Promise<string> {
  const privateKey = Buffer.from(keychain("rsvp-factory-app-key"), "base64").toString("utf8");
  const now = Math.floor(Date.now() / 1000);
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString("base64url");
  const unsigned = `${encode({ alg: "RS256", typ: "JWT" })}.${encode({ iat: now - 60, exp: now + 540, iss: config.githubAppId })}`;
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

export function gh(token: string, ...args: string[]): string {
  return run("gh", [...args, "--repo", REPO], { GH_TOKEN: token });
}

export async function api<T>(token: string, path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`https://api.github.com/repos/${REPO}${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${token}`, Accept: "application/vnd.github+json" },
  });
  if (!response.ok) throw new Error(`GitHub ${init.method ?? "GET"} ${path}: ${response.status} ${await response.text()}`);
  return (await response.json()) as T;
}

export async function graphql<T>(token: string, query: string, variables: Record<string, unknown>): Promise<T> {
  const response = await fetch("https://api.github.com/graphql", {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify({ query, variables }),
  });
  const result = (await response.json()) as { data: T; errors?: { message: string }[] };
  if (result.errors?.length) throw new Error(`GitHub GraphQL: ${result.errors.map((e) => e.message).join("; ")}`);
  return result.data;
}

export type Thread = { id: string; isResolved: boolean; path: string; line: number | null; body: string };

/** The PR's review threads, with the first comment of each. */
export async function reviewThreads(token: string, pr: number): Promise<Thread[]> {
  const [owner, name] = REPO.split("/");
  const data = await graphql<{ repository: { pullRequest: { reviewThreads: { nodes: {
    id: string; isResolved: boolean; path: string; line: number | null; comments: { nodes: { body: string }[] };
  }[] } } } }>(token, `query($owner: String!, $name: String!, $pr: Int!) {
    repository(owner: $owner, name: $name) { pullRequest(number: $pr) { reviewThreads(first: 100) { nodes {
      id isResolved path line comments(first: 1) { nodes { body } } } } } } }`, { owner, name, pr });
  return data.repository.pullRequest.reviewThreads.nodes.map((t) => ({ ...t, body: t.comments.nodes[0]?.body ?? "" }));
}

/** Reply on a review thread and, unless `keepOpen`, resolve it. */
export async function settleThread(token: string, threadId: string, reply: string | undefined, keepOpen = false) {
  if (reply) {
    await graphql(token, `mutation($id: ID!, $body: String!) {
      addPullRequestReviewThreadReply(input: { pullRequestReviewThreadId: $id, body: $body }) { comment { id } } }`,
      { id: threadId, body: reply });
  }
  if (!keepOpen) {
    await graphql(token, `mutation($id: ID!) { resolveReviewThread(input: { threadId: $id }) { thread { id } } }`, { id: threadId });
  }
}

/** Push a local branch as the app. Fast-forward only unless `force`, so a human's commits are never lost. */
export function push(token: string, branch: string, force = false) {
  const auth = Buffer.from(`x-access-token:${token}`).toString("base64");
  run("git", ["-c", `http.https://github.com/.extraheader=AUTHORIZATION: basic ${auth}`, "push",
    ...(force ? ["--force"] : []), `https://github.com/${REPO}.git`, `${branch}:${branch}`]);
}

// --- CI on factory PRs ---

export type FactoryPr = { number: number; headRefName: string; headRefOid: string; labels: { name: string }[] };

export function openFactoryPrs(token: string, prefix: string): FactoryPr[] {
  const prs = JSON.parse(gh(token, "pr", "list", "--state", "open", "--json", "number,headRefName,headRefOid,labels")) as FactoryPr[];
  return prs.filter((pr) => pr.headRefName.startsWith(prefix));
}

/** The `ci` check on a commit: undefined while it hasn't finished. */
export async function ciResult(token: string, sha: string): Promise<{ failed: boolean; runId: string } | undefined> {
  const { check_runs } = await api<{ check_runs: { status: string; conclusion: string; details_url: string }[] }>(
    token, `/commits/${sha}/check-runs?check_name=ci`);
  const check = check_runs[0];
  if (!check || check.status !== "completed") return undefined;
  const runId = check.details_url.match(/runs\/(\d+)/)?.[1] ?? "";
  return { failed: check.conclusion === "failure", runId };
}

/** The failed steps' log, trimmed to its end, where Gradle and PIT print the cause. Read with the maintainer's
 *  gh login: the factory app has no Actions permission, and this is read-only. */
export function failedLog(runId: string): string {
  const log = run("gh", ["run", "view", runId, "--log-failed", "--repo", REPO]);
  const lines = log.split("\n").map((line) => line.replace(/^[^\t]*\t[^\t]*\t\S+Z /, ""));
  return lines.slice(-300).join("\n").slice(-40_000);
}

const ROUND = /<!-- factory-ci-round sha=([0-9a-f]+) -->/g;

/** Commits the factory has already tried to fix CI on, from markers in its own PR comments. */
export function ciRoundsTried(token: string, pr: number): string[] {
  const bodies = JSON.parse(gh(token, "pr", "view", String(pr), "--json", "comments")).comments as { body: string }[];
  return bodies.flatMap((c) => [...c.body.matchAll(ROUND)].map((m) => m[1]));
}

export function roundMarker(sha: string): string {
  return `<!-- factory-ci-round sha=${sha} -->`;
}
