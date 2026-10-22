# 2026-10-21: A brake that only ever failed

**What happened**

- The factory re-ran ticket #3 (PR #26) with the maintainer's security decisions and the new testing rule. It honoured both: demo members moved to a dev/test-only Flyway location, and the HTTP tests live in `app/`. The second opinion confirmed the decisions were applied and found three new things, two of them schema one-way doors.
- CI failed on mutation testing: 45 mutations, 0 killed, all "no coverage", even though the PR had tests in `app/`. The watcher immediately started CI fix round 1.
- The cause was the mutation-testing configuration, not the code: with `-PpitTargets`, PIT also limits which tests it runs to the target classes, so it ran none. The fix round was stopped before it changed anything (it would have spent two sessions on an unfixable problem, and the build configuration is off-limits to agents).
- It also meant the earlier "proof" of the threshold (0 of 39 killed on PR #22) was weaker than claimed: it would have been 0% with good tests too. The conclusion about #22 still held, but the evidence didn't show it.
- Fixed by always running every test while mutating only the changed classes, then checked both directions on #26's branch: with its tests, 40 of 45 killed (89%), pass; with its `app/` tests removed, 0%, fail.

**Struggled with**

- A check that has only ever been seen failing hasn't been tested. The first "proof" planted the failure it expected and stopped there.
- An automatic fix loop amplifies a broken brake: the factory would have tried to fix good code to satisfy a check that couldn't pass.

**Slide material**

- Test every brake in both directions: it must fail on bad code *and* pass on good code.
- Automatic fix loops need trustworthy checks, or they spend real effort chasing a broken signal. Keep the build configuration off-limits to the agents.
