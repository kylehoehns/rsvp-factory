# Auto-merge low-risk factory PRs in stages, with a stop rule written first

The factory can merge its own PRs when they are low risk, but only in stages set by `autoMerge` in `.factory/config.json`: `off`, then `shadow` (the factory comments "would auto-merge" and the maintainer still merges), then `on`. A PR qualifies only if it is in the low tier, `ci` and `risk-gate` are green on its head commit, it has no `decision-needed` or `agent:blocked` label, and every review thread is resolved. The merge is a squash pinned to the head commit that was checked.

**Stop rule, decided before turning it on:** if any auto-merged PR is reverted, auto-merge pauses (qualifying PRs get a "paused" comment instead) until the maintainer looks into it and switches it back on. **Turning it on** waits for shadow-mode data in `docs/factory/metrics.md`: low-tier PRs merged as-is by the maintainer, with none changed or reverted, over at least a week.

**What happened:** on 2026-10-22 the maintainer turned it on after one day, with no low-tier factory PRs yet, once tests-only PRs became low. The stop rule is the safety net instead of the week of data.

The published rollouts follow the same shape: Vercel decided in advance to stop if reverts exceeded three times its 1.7% baseline (671 low-risk PRs skipped review with no reverts); Rewind ran a week of shadow mode and saw about 99% agreement before enabling. Every published low tier excludes schema and interface changes, so schema changes never auto-merge here either. Small feature PRs can, once the independent second opinion passes them (ADR 0003).

## Consequences

- With one maintainer, a revert-based stop rule is coarse; a single revert pauses everything, which is the intent.
- GitHub's own auto-merge button isn't used: it reportedly can't be armed until requirements are met, and the factory already polls, so it merges through the API when they are.
