# How the factory works

The factory turns a GitHub issue labelled `ready-for-agent` into a reviewed pull request. It is switched on by the repository variable `FACTORY_ENABLED=true` and off by anything else, so the maintainer can stop it from the GitHub settings page, phone included.

```text
ticket labelled ready-for-agent (by a maintainer)    or a factory PR merges, or "Run workflow"
  │
  ▼ factory.yml
  dispatch   picks the lowest-numbered ready ticket whose blockers are all closed,
             if fewer than wipLimit factory tickets/PRs are open; labels it agent:in-progress
  implement  headless Claude on a throwaway runner: tdd at the spec's seams, an acceptance
             scenario, ./gradlew check green, commits, PR body (pr skill), outcome.json
  publish    rejects changes to the factory's own controls, pushes agent/issue-N as the
             factory app, opens a draft PR labelled agent:review
  │
  ▼ factory-review.yml (on the agent:review label)
  review     a fresh session runs the code-review skill, fixes what it finds, rewrites the body
  publish    pushes the fixes, marks the PR ready for review
  │
  ▼ ci + risk-gate
  the maintainer approves medium/high PRs; merging closes the ticket and dispatches the next one
```

Anything that goes wrong ends in `agent:blocked` on the ticket or PR, with a comment saying why. To retry, remove `agent:blocked` and re-apply `ready-for-agent`.

## Knobs (`.factory/config.json`)

| Setting | Default | Meaning |
|---|---|---|
| `runner` | `claude-headless` | Which agent `run-agent.sh` starts |
| `wipLimit` | 1 | Factory tickets and open factory PRs at once |
| `implementModel`, `reviewModel` | `sonnet` | Model per role |
| `implementMaxTurns`, `reviewMaxTurns` | 200, 80 | Hard stop per session |
| `implementTimeoutMinutes`, `reviewTimeoutMinutes` | 45, 20 | Hard stop per session (enforced in the workflows) |

## Credentials

- `CLAUDE_CODE_OAUTH_TOKEN` (secret): the model credential, used only in the implement and review steps.
- `FACTORY_APP_ID` (variable) and `FACTORY_APP_PRIVATE_KEY` (secret): the factory's GitHub App, which opens and updates PRs. Because the app is the author, the maintainer can approve its PRs, and its pushes trigger CI (pushes made with the default Actions token don't).
