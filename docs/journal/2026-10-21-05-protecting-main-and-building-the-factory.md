# 2026-10-21: Protecting main and building the factory
**What happened**

- The maintainer ran `/install-github-app` at the laptop, which stored the subscription token and added two starter workflows. One of them reviewed every PR automatically with an unpinned marketplace plugin, which would have spent usage on docs-only PRs and duplicated the planned reviewer. Made it opt-in (a `claude-review` label) and restricted `@claude` to collaborators.
- Protected `main` with a ruleset: PRs only, squash merges, `ci` required, no force pushes or deletion, no bypass for anyone. Required approvals stay at 0 because a solo maintainer can't approve their own PRs; the `risk-gate` check takes over that job when it leaves shadow mode. A probe push straight to `main` was rejected.
- The maintainer created the factory's GitHub App (`FACTORY_APP_ID`, `FACTORY_APP_PRIVATE_KEY`): its PRs can be approved by the maintainer, and its pushes trigger CI.
- Built factory v1 on GitHub Actions: a deterministic dispatcher (6 tests), implementer and reviewer prompts, and a publisher that was the only step allowed to write to GitHub.
- Before it ever ran, the maintainer asked for it to run locally instead. Rebuilt it around Matt Pocock's Sandcastle: each ticket gets a Docker sandbox on its own worktree; a script on the maintainer's machine dispatches, runs an implementer and a fresh reviewer session in the sandbox, then pushes and opens the PR as the factory app. Credentials (a Claude token for the sandbox, the app's private key) live in the macOS keychain. ADR 0004 records the design and the Actions version it replaced.
- The hard part was Testcontainers inside a sandbox: tests need Docker, and mounting the host's Docker socket would hand the agent the host's Docker. Each run instead gets a private Docker-in-Docker on its own network. Proved with a probe before writing the orchestrator: the acceptance test started Postgres inside the sandbox and passed.

**Went well**

- Linting the factory before running it: actionlint caught that the `runner` context isn't available in job-level `env`, so every file path in the agent jobs would have been empty on the first run. Both linters, and a type-check of the orchestrator, now run in CI.
- Probing the riskiest assumption first (Testcontainers in a sandbox) before writing 200 lines that depend on it.

**Struggled with**

- Starter templates are written for "works on any repo", not for a factory with a budget: the generated review workflow would have reviewed every PR, unpinned. Read generated config before trusting it.
- Public CI has a non-obvious leak path: uploaded artifacts are downloadable by anyone and are not secret-masked like logs. (Moot once the factory moved local, but worth a slide.)
- Colima only shares the home directory with Docker, so a worktree under `/tmp` mounted as an empty folder.

**Slide material**

- Three trust levels per run: the agent never holds a GitHub credential; deterministic code validates and publishes its work. That held when the factory moved from Actions to a laptop: the harness changed, the brakes didn't.
- "The agent gets its own Docker, not yours."
- The factory needs its own identity, or its PRs can't be approved and don't trigger CI.
- Lint the factory like you lint the app.
