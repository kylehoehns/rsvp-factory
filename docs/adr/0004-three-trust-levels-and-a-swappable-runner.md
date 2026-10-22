# Three trust levels in every factory run, and a swappable runner

Every factory run is split into jobs with different powers. The deterministic dispatcher and publisher hold the factory app's GitHub token; the agent job in between holds only a Claude credential and a read-only checkout, and hands its work over as a git bundle plus an outcome file that the publisher validates (it refuses any change to `.github`, `.factory`, `.agents`, `.claude`, `.githooks` or `scripts`). An agent that is misled by text in an issue can therefore write bad code, which review and the risk gate exist to catch, but cannot push, merge, relabel or change the factory itself.

The agent is started through `.factory/run-agent.sh`, chosen by `runner` in `.factory/config.json`. The first runner is plain headless `claude -p`, because it proves the loop with the fewest moving parts. Matt Pocock's Sandcastle (sandboxed, agent-agnostic loops) is the intended second runner; adding it changes that script and the config, not the workflows.

## Consequences

- The agent job still holds the Claude credential, so a prompt-injected agent could leak it. Mitigations: only a maintainer's `ready-for-agent` label starts work, the runner is thrown away after each run, and the credential can be rotated or swapped for a spend-capped API key.
- Agents may change build files (a ticket can need a new dependency); those PRs land in the high risk tier instead of being refused.
