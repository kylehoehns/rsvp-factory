# The factory runs locally in Sandcastle sandboxes; only the orchestrator talks to GitHub

The factory runs on the maintainer's machine, not in GitHub Actions. Each ticket gets a Sandcastle Docker sandbox on its own branch and worktree, with a private Docker-in-Docker on a dedicated network so Testcontainers works without the agent reaching the host's Docker. Agents hold a Claude credential and nothing else: no GitHub token, no `gh`. The orchestrator (`.factory/sandcastle/factory.ts`, now in [factory-kit](https://github.com/kylehoehns/factory-kit), ADR 0008) is the only part that talks to GitHub, as the factory's GitHub App, with the app key read from the macOS keychain. It treats what agents produce as data: it refuses any branch that touches `.github`, `.factory`, `.agents`, `.claude`, `.githooks` or `scripts` before pushing.

## Considered options

- **GitHub Actions on hosted runners** (built first, then replaced at the maintainer's request). Free for a public repo and isolated by throwaway VMs, but run logs are public and the factory is harder to watch and interrupt. The same trust split applied: the agent job had no GitHub write token.
- **Plain headless `claude -p` on the host with a tool allowlist.** Least setup, but allowed commands run as the maintainer with access to the whole disk.
- **Mounting the host's Docker socket into the sandbox** for Testcontainers. Simple, but it hands the agent the host's Docker daemon, which defeats the sandbox. A private Docker-in-Docker costs a privileged container inside the Docker VM instead.

## Consequences

- An agent misled by text in an issue can write bad code (review and the risk gate exist to catch that) but can't push, merge, relabel or change the factory.
- The Claude credential is inside the container, so a prompt-injected agent could leak it. Mitigations: only a maintainer's `ready-for-agent` label starts work, the token can be rotated, and a spend-capped API key can replace it.
- Agents may change build files (a ticket can need a new dependency); those PRs land in the high risk tier instead of being refused.
