# 2026-10-21: The factory fixes its own CI, and keeps running

**What happened**

- The maintainer wanted factory PRs to react to CI on their own: if CI finds something the sandbox missed, the factory should read why and fix it, without anyone re-running anything.
- The factory became a watcher: `npm run factory` keeps running and, every minute, first looks for factory PRs whose `ci` check failed on their latest commit, then picks up newly ready tickets. Merging a PR is now enough to start the next ticket.
- A red PR gets a fix session in a sandbox on its branch, with the failed steps' log as input: find the cause, fix the code or tests, never weaken the check. The fix is pushed fast-forward as the factory app with a comment "CI round N of 2". After two rounds the PR is labelled `agent:blocked` for the maintainer. That's Stripe's published cap.
- Which commits already had a round is tracked by hidden markers in the factory's own PR comments, so a restart doesn't repeat or forget a round.
- The orchestrator was split into four files (shared, GitHub, sandbox, loop) on the way, since adding this would have broken the repo's own 5,000-token limit.

**Slide material**

- "At most two CI rounds, then a human": automatic, but bounded.
- A local factory needs something running to notice: a watcher loop is the simplest event system.
- The size limit shapes the factory's own code too.
