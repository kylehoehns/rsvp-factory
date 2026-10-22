# Build journal

A running record of building this repo, for the talk. It is written in neutral terms ("the maintainer", "the research"), with no personal details or quotes from working sessions: what we did, what went well, what we struggled with, and what surprised us. One file per entry, numbered in order; each ends with **Slide material** worth lifting. Add a new entry as the next numbered file and a row in the table below.

The research behind the design lives outside this repo (a long report on software factories as of October 2026); this journal is about building the thing.

## Running tallies

Updated as entries are added.

**Went well**
- Pocock's skills drove the human-in-the-loop part with almost no adaptation: `/grill-with-docs` turned 22 decisions into a glossary and an ADR in three short rounds.
- Brakes were cheap to add and easy to prove: each one was verified by planting a defect and watching the build fail.
- Pinning everything (versions, image tags, action SHAs, skills) cost minutes and removed whole classes of surprise.

**Struggled with**
- Over-applying an outlier: the first acceptance design copied StrongDM's locked, human-written scenarios, which no other published factory does. It made a human write tests before any work could start. Backed out (ADR 0002).
- Local Docker plumbing (Colima) broke twice before any code was written.
- Tooling assumptions from 2025 were stale by late 2026 (PIT incremental analysis went commercial; GitHub moved `ubuntu-latest`).
- Fast-moving facts: two research claims had to be corrected after the first report draft.

**Open questions to answer by the end**
- How much maintainer time does a merged agent PR actually cost?
- How often does the risk classifier disagree with the maintainer?
- Did the brakes catch real agent mistakes, or only planted ones? (First real catch: the second opinion on #22. First silent brake: mutation testing with no tests in `app/`.)
- Feature flags as the release brake: if every agent-built feature ships behind a default-off flag, merging becomes a two-way door and "release" becomes a separate, reversible decision (per-member beta, then a metrics-gated percentage ramp with automatic rollback). Planned as spec #2, with monitoring and alerting.

---

## Entries

| # | Entry |
|---|---|
| 1 | [Research, then brakes first](2026-10-21-01-research-then-brakes-first.md) |
| 2 | [Spec and tickets](2026-10-21-02-spec-and-tickets.md) |
| 3 | [Should a human write the tests first?](2026-10-21-03-should-a-human-write-the-tests-first.md) |
| 4 | [Risk gate in shadow mode, and a file-size brake](2026-10-21-04-risk-gate-in-shadow-mode-and-a-file-size-brake.md) |
| 5 | [Protecting main and building the factory](2026-10-21-05-protecting-main-and-building-the-factory.md) |
| 6 | [Giving the factory a way to ask](2026-10-21-06-giving-the-factory-a-way-to-ask.md) |
| 7 | [The first factory runs (four attempts)](2026-10-21-07-the-first-factory-runs-four-attempts.md) |
| 8 | [The first factory PR, and making review visible](2026-10-21-08-the-first-factory-pr-and-making-review-visible.md) |
| 9 | [A second opinion, and a brake that was silently off](2026-10-21-09-a-second-opinion-and-a-silent-brake.md) |
| 10 | [The factory fixes its own CI, and keeps running](2026-10-21-10-the-factory-fixes-its-own-ci.md) |
| 11 | [A brake that only ever failed](2026-10-21-11-a-brake-that-only-ever-failed.md) |
| 12 | [Cross-checking against the consensus, and triaging findings](2026-10-22-12-cross-checking-and-triaging-findings.md) |
| 13 | [Three tickets in flight](2026-10-22-13-three-in-flight.md) |
| 14 | [The human is the queue](2026-10-22-14-the-human-is-the-queue.md) |
