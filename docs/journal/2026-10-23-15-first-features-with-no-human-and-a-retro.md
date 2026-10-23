# 2026-10-23: The first features with no human, and a retro

**What happened**

- **Tests-only PRs became low** (#51). A PR that only adds lines under `app/src/test` or `acceptance/src/test` can't change behaviour, so it can only add coverage.
- **Auto-merge went from shadow to on** (#54), a week earlier than ADR 0006 planned. The stop rule (any revert of an auto-merged PR pauses it) is the safety net.
- **Letting the factory approve itself was refused.** The first design let the factory's own reviewer mark a PR as passed. The agent's guardrails blocked the change as self-approval, and the published designs agree: the author's own system can't be the approver. The pass moved to the second opinion on GitHub Actions instead, which runs a prompt the factory can't change.
- **Small feature PRs can now be low** (#57). A PR is low when the independent second opinion passed its head commit (it matches the ticket and `CODING_STANDARDS.md`, with no blocker or one-way door) and it changes at most 200 production lines. Tests don't count toward that limit: they were 60–80% of every PR, and the largest medium PR changed 132 production lines.
- **A silent brake, found by accident.** The gate read the factory bot's name from a repo variable that was never set. Every factory PR counted as an outside author, so nothing the factory opened could ever have been low.
- **#56 and #55 merged with no human.** The factory wrote and reviewed them, the second opinion passed them, the gate re-rated them low, and the factory merged them. Spec #1 finished the next day: 13 tickets, all closed.

**Retro**

- 15 factory PRs: 13 merged, 0 reverted. Median 0.4 hours from PR to merge; the two slow ones (5 and 27 hours) were waiting on the maintainer. 2 to 4 agent sessions and 12k to 72k output tokens per PR.
- 23 PRs built or fixed the factory itself, against 13 for the product. The factory was more work than the features.
- **Went well:** it fixes red CI and merge conflicts by itself; it stopped for the one schema decision that mattered; follow-ups make "not now" cheap to say.
- **Went badly:** brakes that were silently stuck (mutation tests that only failed, the unset bot name, an old workflow running on PRs opened before the change); running 3 at once caused collisions git can't see; ticket scope leaked (#41 built #7's rules).
- **Unknown:** the second opinion ran about 8 times and found no blockers. It may be working or it may be a rubber stamp. The decision is to keep measuring and change nothing yet.

**Decisions**

- Keep the second opinion as it is, and track what it finds.
- Don't run the factory as a background service yet; it runs while a session watches it.
- Slice tickets at seams: when two tickets touch the same rule, the one that doesn't own it says so in a `Not in this ticket` section, and the implementer stops at its acceptance criteria.
- Next: spec #2, feature flags (with monitoring and alerting to follow).

**Slide material**

- "The factory approving itself was blocked, and it should have been." Author and approver have to be different systems.
- "Lines changed is the wrong risk measure when most of the lines are tests."
- "Every brake we built had a way to be silently off. Find them by running real work, not by reading config."
