# Risk tiers for a repo with one maintainer

Every PR gets a risk tier that decides how much review it needs: **low** merges automatically once checks pass, **medium** needs the maintainer's approval on the head commit, and **high** needs the maintainer's approval plus a second, independent AI review and the slow test suite, and is never auto-merged. Published risk-tier schemes (the Claude Code team's, October 2026) ask for two people on high-risk changes; this repo has one, so the high tier adds everything one person can add instead, and an AI review never counts as a person.

The tier is set by the change, not by its author. Path rules in `.github/risk-policy.toml` set a floor (identity, migrations and the factory's own controls are high; anything unlisted is medium; small docs changes, and PRs that only add tests, are low). The policy is read from the base branch, so a PR can't loosen the rules that judge it, and a classifier added later may raise the tier but never lower it. The gate runs in shadow mode first: it labels every PR and blocks nothing, so its calls can be compared with the maintainer's before it is trusted.

## Consequences

- The gate workflow uses `pull_request_target` to get a write token, which is only safe because it never checks out or runs PR code.
- A workflow added in a PR doesn't run on that PR (GitHub runs `pull_request_target` workflows from the base branch), so the gate first judges the PR after the one that adds it.
