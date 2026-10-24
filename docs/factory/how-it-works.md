# How the factory works

The factory turns GitHub issues labelled `ready-for-agent` into reviewed pull requests, and keeps its own pull requests' CI green. Its code lives in [factory-kit](https://github.com/kylehoehns/factory-kit), which can watch several repos; this repo is one of them. It runs on the maintainer's machine: start it once with `npm run factory` in factory-kit and it checks GitHub every `pollSeconds` until stopped with Ctrl+C (`npm run factory -- --once` does a single pass). How it treats this repo is set in [`.factory/config.yml`](../../.factory/config.yml). In Matt Pocock's terms it is the "night shift": the unattended half, between the human "day shift" that shapes tickets and the one that reviews and merges PRs.

```text
cd factory-kit && npm run factory          (every pollSeconds, until Ctrl+C)
  │
  update     for each open factory PR that conflicts with main (another PR merged first): a session merges
             main in, resolves the conflict, renumbers its own migrations if they collide; at most
             maxUpdateRounds, then agent:blocked
  fix CI     for each open factory PR whose `ci` check failed on its latest commit: a fix session in a
             sandbox on that branch, given the failed steps' log; pushes the fix and comments
             "CI round N of maxCiRounds". After the last round it labels the PR agent:blocked.
  review     for each open factory PR with a new second opinion: blockers get a review-fix round (at most
             maxReviewRounds), then the second opinion runs again; one-way doors get decision-needed;
             follow-ups are listed on the PR. Each handled finding's thread gets a reply and is resolved.
  auto-merge for each open low-risk factory PR that's green with nothing left open: in `shadow` mode a
             "would auto-merge" comment, when `on` a squash merge (ADR 0006; a revert pauses it)
  follow-ups for each merged factory PR, its listed follow-ups are filed as needs-triage tickets
  │
  for each ready ticket (lowest number first, all blockers closed, under wipLimit)
  │
  dispatch   the factory claims it (agent:in-progress) and writes .factory/runs/task.md
  sandbox    Sandcastle starts a Docker container on branch agent/issue-N (a git worktree), plus a
             private Docker-in-Docker on its own network for Testcontainers
  implement  Claude, in the container: tdd at the spec's seams, an acceptance scenario,
             ./gradlew check green, commits, PR body (pr skill), outcome
  review     a fresh Claude session in the same container: code-review skill, fixes, PR body
  publish    factory.ts refuses changes to the factory's own controls, pushes the branch, opens the
             PR as the factory app, and posts the reviewer's findings as a review on it
  2nd opinion  it adds the claude-review label to every PR, which starts the independent second opinion
             (.github/workflows/second-review.yml, which calls factory-kit's); its pass on the head commit can make a small PR low
  │
  ci + risk-gate on the PR; low merges itself; medium/high merge once the maintainer approves the head
  commit; merging closes the ticket
```

When an agent isn't sure how to continue, what happens depends on the kind of decision:

| The agent hits… | It… | The ticket ends up |
|---|---|---|
| A two-way door (a name, a status code, an internal structure) | Decides, and records the choice in the PR body | In a PR, where the maintainer sees the call |
| A one-way door (an API shape or schema the spec doesn't settle, a spec that contradicts itself) | Stops and leaves its questions as a comment on the ticket | `needs-info`. Answer in a comment and re-apply `ready-for-agent`; the next run reads every comment. |
| Something broken (build or tooling fails, `./gradlew check` stays red) | Stops and says why | `agent:blocked`. Fix the cause, remove the label and re-apply `ready-for-agent`. |

Most questions should never reach the factory: grilling and `/to-spec` exist to answer them while a human is in the loop.

## One-time setup

1. Docker running (Colima or Docker Desktop) and Node 26.
2. Clone [factory-kit](https://github.com/kylehoehns/factory-kit), then `npm ci`, and copy `factory.example.yml` to `factory.yml` with this repo in `repos`.
3. `npm run build-images` builds this repo's sandbox from [`.factory/Dockerfile`](../../.factory/Dockerfile).
4. `bin/setup-keychain.sh` stores two credentials in the macOS keychain:
   - a Claude subscription token from `claude setup-token` (agents in containers can't use your Mac's login);
   - the factory GitHub App's private key, so the shift can open PRs as the app.

## What the agent can and can't reach

| Agent can | Agent can't |
|---|---|
| Read and write its worktree (bind-mounted) | See the rest of your disk |
| Use a dedicated Gradle cache (`~/.cache/factory/rsvp-factory/gradle`) | Touch your own `~/.gradle` |
| Start containers in its private Docker-in-Docker | Reach your Docker daemon or its other containers |
| Call the Anthropic API and download dependencies | Use GitHub: no `gh`, no token, no push |

The repo's hooks run inside the container with `FACTORY_RUN=1`, so the factory's own controls, committed migrations and shipped acceptance scenarios can't be edited in-session; the factory re-checks the branch against `protected` in `.factory/config.yml` before pushing.

## Knobs

In this repo's [`.factory/config.yml`](../../.factory/config.yml), read from `main`:

| Setting | Meaning |
|---|---|
| `check` | What "done" means: `./gradlew check` |
| `autoMerge` | `off`, `shadow` (comment only, the starting point) or `on` (merge qualifying low-risk PRs). See ADR 0006 for when to switch |
| `maintainers`, `factoryBot` | Whose approvals and PRs count |
| `protected` | The factory's own controls, which agents can't change |
| `sandbox` | The private Docker for Testcontainers, and the Gradle cache |
| `risk` | The tiers (ADR 0003) |

In factory-kit's `factory.yml`, on the maintainer's machine: the app, `wipLimit` (shared by every repo the factory watches; at 3 it keeps building independent tickets while earlier PRs wait), fix-round limits, and the model and timeout per session.

Session logs go to `.factory/runs/logs/` and stay on the machine (gitignored). Every agent session also appends its outcome, duration, commits and token totals to `.factory/runs/metrics.jsonl`; `python3 scripts/factory_metrics.py` combines that with the factory PRs on GitHub into [`metrics.md`](metrics.md): outcomes, rounds, time to merge, reverts, and each risk tier against what the maintainer did.

Agents commit as `rsvp-factory-bot[bot]` (set in the sandbox's environment), so GitHub shows the factory, not the maintainer, as the author of agent code.
