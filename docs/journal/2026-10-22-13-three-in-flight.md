# 2026-10-22: Three tickets in flight

**What happened**

- With findings triage, measurement, a work-in-progress limit of 3 and shadow auto-merge merged, the factory ran #4, #5 and #28 one after another and opened #33, #36 and #37.
- **First automatic CI fix.** #33 failed CI on stale generated module docs; the factory read the log, committed the regenerated docs as the bot, and CI went green on round 1 of 2. #36 hit the same failure and got the same fix. #37 was green first time. The second opinion ran automatically on #36 and #37, found no blockers, and the factory triaged both.
- **Parallelism found three new problems**, none of them in agent-written code:
  1. The dispatcher re-dispatched #4 right after its PR opened (publishing releases the claim; the ticket stays open until its PR merges). Fixed: tickets with an open factory PR are skipped.
  2. #36 and #37 both added a `V3` migration: no git conflict, but Flyway refuses duplicate versions. And #36 conflicted with `main` once #33 merged. Fixed: the factory now updates its own conflicting PRs (merge `main`, keep both sides, renumber its own migrations), capped like CI rounds; a migration is frozen only once it's on `main`.
  3. Committed generated docs went stale on `main` itself: three PRs regenerated them from different starting points and their squash merges stitched the files together. Fixed by not committing them; they're a build output now.
- A fourth near-miss: #28 was nearly built twice in the seconds between its PR merging and GitHub closing the ticket. The dispatcher now also skips tickets with a merged factory PR.

**Struggled with**

- Every one of these only appears with more than one PR in flight. A factory that works at WIP 1 has not been tested at WIP 3.
- Committing generated files is a habit that's harmless for a human team and a steady source of failures for a parallel factory.

**Slide material**

- "The first automatic CI fix": a red build, a bot commit, green, nobody involved.
- Parallel PRs collide in places git can't see: migration version numbers, generated files.
- Remove failure classes instead of fixing failures: generated docs stopped being committed.
