# How the factory works

The factory turns GitHub issues labelled `ready-for-agent` into reviewed pull requests, and keeps its own pull requests' CI green. It runs on the maintainer's machine: start it once with `npm run factory` and it checks GitHub every `pollSeconds` until stopped with Ctrl+C (`npm run factory -- --once` does a single pass). In Matt Pocock's terms it is the "night shift": the unattended half, between the human "day shift" that shapes tickets and the one that reviews and merges PRs.

```text
cd .factory/sandcastle && npm run factory          (every pollSeconds, until Ctrl+C)
  │
  fix CI     for each open factory PR whose `ci` check failed on its latest commit: a fix session in a
             sandbox on that branch, given the failed steps' log; pushes the fix and comments
             "CI round N of maxCiRounds". After the last round it labels the PR agent:blocked.
  review     for each open factory PR with a new second opinion: blockers get a review-fix round (at most
             maxReviewRounds), then the second opinion runs again; one-way doors get decision-needed;
             follow-ups are listed on the PR. Each handled finding's thread gets a reply and is resolved.
  follow-ups for each merged factory PR, its listed follow-ups are filed as needs-triage tickets
  │
  for each ready ticket (lowest number first, all blockers closed, under wipLimit)
  │
  dispatch   .factory/dispatch.py claims it (agent:in-progress) and writes .factory/runs/task.md
  sandbox    Sandcastle starts a Docker container on branch agent/issue-N (a git worktree), plus a
             private Docker-in-Docker on its own network for Testcontainers
  implement  Claude, in the container: tdd at the spec's seams, an acceptance scenario,
             ./gradlew check green, commits, PR body (pr skill), outcome
  review     a fresh Claude session in the same container: code-review skill, fixes, PR body
  publish    factory.ts refuses changes to the factory's own controls, pushes the branch, opens the
             PR as the factory app, and posts the reviewer's findings as a review on it
  high tier  if the risk policy puts the PR in the high tier, it adds the claude-review label, which
             starts the independent second opinion (.github/workflows/claude-code-review.yml)
  │
  ci + risk-gate on the PR; the maintainer approves; merging closes the ticket
```

When an agent isn't sure how to continue, what happens depends on the kind of decision:

| The agent hits… | It… | The ticket ends up |
|---|---|---|
| A two-way door (a name, a status code, an internal structure) | Decides, and records the choice in the PR body | In a PR, where the maintainer sees the call |
| A one-way door (an API shape or schema the spec doesn't settle, a spec that contradicts itself) | Stops and leaves its questions as a comment on the ticket | `needs-info`. Answer in a comment and re-apply `ready-for-agent`; the next run reads every comment. |
| Something broken (build or tooling fails, `./gradlew check` stays red) | Stops and says why | `agent:blocked`. Fix the cause, remove the label and re-apply `ready-for-agent`. |

Most questions should never reach the factory: grilling and `/to-spec` exist to answer them while a human is in the loop.

## One-time setup

1. Docker running (Colima or Docker Desktop) and Node 22+.
2. `cd .factory/sandcastle && npm ci && npm run build-image`
3. `.factory/sandcastle/setup-keychain.sh` stores two credentials in the macOS keychain:
   - a Claude subscription token from `claude setup-token` (agents in containers can't use your Mac's login);
   - the factory GitHub App's private key, so the shift can open PRs as the app.

## What the agent can and can't reach

| Agent can | Agent can't |
|---|---|
| Read and write its worktree (bind-mounted) | See the rest of your disk |
| Use a dedicated Gradle cache (`~/.cache/rsvp-factory/gradle`) | Touch your own `~/.gradle` |
| Start containers in its private Docker-in-Docker | Reach your Docker daemon or its other containers |
| Call the Anthropic API and download dependencies | Use GitHub: no `gh`, no token, no push |

The repo's hooks run inside the container with `RSVP_FACTORY=1`, so the factory's own controls, committed migrations and shipped acceptance scenarios can't be edited in-session; `factory.ts` re-checks the branch before pushing.

## Knobs (`.factory/config.json`)

| Setting | Meaning |
|---|---|
| `wipLimit` | Tickets and open factory PRs at once (1: one PR waits for the maintainer before the next starts) |
| `implementModel`, `reviewModel` | Model per role |
| `implementTimeoutMinutes`, `reviewTimeoutMinutes` | Hard stop per session |
| `githubAppId` | The factory app (public; the private key is in the keychain) |

Session logs go to `.factory/runs/logs/` and stay on the machine (gitignored).
