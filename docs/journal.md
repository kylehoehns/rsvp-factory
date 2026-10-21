# Build journal

A running record of building this repo, for the talk: what we did, what went well, what we struggled with, and what surprised us. Newest entries at the bottom. Each entry ends with **Slide material** worth lifting.

The research behind the design lives outside this repo (a long report on software factories as of October 2026); this journal is about building the thing.

## Running tallies

Updated as entries are added.

**Went well**
- Pocock's skills drove the human-in-the-loop part with almost no adaptation: `/grill-with-docs` turned 22 decisions into a glossary and an ADR in three short rounds.
- Brakes were cheap to add and easy to prove: each one was verified by planting a defect and watching the build fail.
- Pinning everything (versions, image tags, action SHAs, skills) cost minutes and removed whole classes of surprise.

**Struggled with**
- Local Docker plumbing (Colima) broke twice before any code was written.
- Tooling assumptions from 2025 were stale by late 2026 (PIT incremental analysis went commercial; GitHub moved `ubuntu-latest`).
- Fast-moving facts: two research claims had to be corrected after the first report draft.

**Open questions to answer by the end**
- How much maintainer time does a merged agent PR actually cost?
- How often does the risk classifier disagree with the maintainer?
- Did the brakes catch real agent mistakes, or only planted ones?

---

## 2026-10-21: Research, then brakes first

**What happened**

- Ran a deep-research pass (nine parallel research tracks) on software factories as of October 2026: case studies, tooling, best practices, evidence and critiques, Pocock's skills, and the Spring/Java stack. Settled on Pocock's framing: a factory is agent sessions *started by triggers*, not the absence of humans, and you build **brakes before accelerators**.
- Two corrections after the first draft. The "Anthropic auto-merges 30k PRs a day" figure is really "30,000+ code changes merged per day to one monorepo", with humans reviewing intent. And the March 2026 line "It won't approve PRs, that's still a human call" was already outdated by October: the Claude Code team now runs three risk tiers, with the lowest merging without a human.
- That second correction became a central design idea: **risk-tiered review**. With one maintainer, the top tier is "maintainer + a second, independent AI review + the full slow test suite", never auto-merged.
- Decisions: community events platform (not a clone of any real product), Gradle Kotlin DSL, Claude Code first but portable via `AGENTS.md` and `.agents/skills`, Sandcastle as the loop runner with a scheduled-Claude fallback, Pocock's `pr` template, a "locked but visible" acceptance suite.
- Built the brakes by hand, one commit each: Initializr baseline → formatter + null-safety → architecture tests + mutation testing → agent layer (vendored skills, AGENTS.md, hooks) → CI. Then the first `/grill-with-docs` session.

**Went well**

- Spring Initializr gave a correct Boot 4.1.1 / Java 25 / Gradle 9.8.2 baseline in one call; starting the history from it makes every later commit a visible, deliberate change.
- Every brake was proven by planting a defect: unformatted code and a `return null` in a `@NullMarked` package both failed with a file and line; an `Instant.now()` call failed the Clock rule with the fix in the message.
- The ArchUnit `because(...)` text doubles as agent instructions: the failure message says what to do instead.
- The grilling session was fast and pleasant: three rounds of numbered questions with recommended answers, mostly accepted with "yes". The skill caught a real language problem (cancel meant two things) and split it into **withdrawal** and **cancelled event**.
- Pocock's setup skill pushed back on the research report: the report proposed a glossary per module, the skill says one glossary fits almost every repo. The skill was right.

**Struggled with**

- Colima's VM wouldn't start (a stale "disk in use" lock), then Testcontainers couldn't find Colima's socket. Fixed with a forced stop and two environment variables, now in `AGENTS.md` because no agent could discover it.
- Spring Boot's `developmentOnly` configuration doesn't inherit the BOM, so the Docker Compose dependency had no version.
- PIT's incremental analysis now needs a commercial plugin; replaced with "CI passes only the classes the PR changed".
- Primary sources were hard to reach: OpenAI's harness-engineering post returned 403 and had to be read from archive.org.

**Slide material**

- "Brakes before accelerators": six commits of checks before a single feature or agent run.
- Every brake is proven by a planted failure, not assumed.
- The 30k-a-day correction as a lesson in how fast this field's facts go stale.
- A failure message is a prompt: write `because(...)` as the fix.
- Grilling: 22 decisions, about 10 minutes, mostly "yes", and one real ambiguity caught.
