# Build journal

A running record of building this repo, for the talk. It is written in neutral terms ("the maintainer", "the research"), with no personal details or quotes from working sessions: what we did, what went well, what we struggled with, and what surprised us. Newest entries at the bottom. Each entry ends with **Slide material** worth lifting.

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
- Did the brakes catch real agent mistakes, or only planted ones?
- Feature flags as the release brake: if every agent-built feature ships behind a default-off flag, merging becomes a two-way door and "release" becomes a separate, reversible decision (per-member beta, then a metrics-gated percentage ramp with automatic rollback). Planned as spec #2, with monitoring and alerting.

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
- The grilling session was fast: three rounds of numbered questions, each with a recommended answer, and most recommendations were accepted as-is. The skill caught a real language problem (cancel meant two things) and split it into **withdrawal** and **cancelled event**.
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
- Grilling: 22 decisions in about 10 minutes, most recommendations accepted, and one real ambiguity caught.

## 2026-10-21: Spec and tickets

**What happened**

- `/to-spec` published the first spec as issue #1: 48 user stories, four modules, an HTTP API contract, and two agreed test seams (the HTTP API, and the seating rules as a pure function).
- `/to-tickets` broke it into 11 tickets (#2–#12): one `ready-for-human` ticket for the locked acceptance suite, and ten `ready-for-agent` vertical slices, each a GitHub sub-issue of the spec with native blocked-by links. The maintainer asked for the largest ticket (RSVP) to be split in two, separating the basic confirmed/waitlisted flow from the guest-limit and cutoff refusals.

**Went well**

- Writing the spec surfaced a design problem before any code existed: if `events` owned capacity, `events` and `rsvp` would depend on each other, which Spring Modulith rejects. Moving the seating plan into `rsvp` fixed it and made `rsvp` a properly deep module. Cheap to change in a spec, expensive in code.
- GitHub's native sub-issues and blocked-by links carry the ticket graph, so the dispatcher can read order from the API instead of an agent guessing it from prose.

**Struggled with**

- A locked acceptance suite written up front would fail every PR until every feature exists. Answer: tag each scenario with its ticket; CI runs scenarios for closed tickets plus the PR's own ticket.
- `/to-spec` labels a spec `ready-for-agent` by default; this repo overrides that to `spec` so the factory never tries to "implement" a whole spec.

**Slide material**

- The spec caught an architecture bug: a dependency cycle found in prose, not in code.
- The ticket graph lives in the tracker, not in an agent's head.
- Acceptance scenarios tagged by ticket: a locked suite you can write before the features.

## 2026-10-21: Should a human write the tests first?

**What happened**

- Started the acceptance ticket as a **locked suite**: every scenario for spec #1 written up front by the maintainer, tagged per ticket, and off-limits to agents. The inspiration was StrongDM's hidden "holdout" scenarios.
- Halfway through, the maintainer challenged it: if you can't start any work until a human has written the tests, that's waterfall. And how would you even do that for a static site or a data pipeline?
- Checking the research answered it: **only StrongDM does this.** Stripe, Spotify, Vercel, Uber, Shopify, Intercom and Anthropic let agents write the tests and catch weak ones afterwards with a separate reviewer, mutation testing and risk-tiered human review. Pocock's own chain works the same way: `tdd` writes tests first, `code-review` checks them against the spec.
- Re-scoped: keep the harness (real app, real Postgres, a movable clock, an HTTP helper), drop the pre-written scenarios, and have **each ticket add its own scenarios**. What's protected is shipped behaviour: changing an existing scenario is at least medium risk, and high if the old scenarios fail against the new code (ADR 0002).
- A judge agent to tell "test refactor" from "test weakening" was considered and deferred: scenarios test at the highest seam (the public API), so refactors behind it shouldn't touch them. Measure first, automate if it turns out to be common.

**Went well**

- Even the abandoned version paid for itself: writing scenarios exposed a contradiction in the spec (a "duplicate RSVP" refusal that a create-or-update endpoint can't produce), fixed before any agent built it.
- The general rule that survived is domain-independent: *before an agent starts a ticket, its definition of done must exist somewhere the agent can't quietly weaken.* For an API that's scenarios; for a data pipeline, golden datasets and data contracts; for a static site, Playwright checks and budgets; for subjective work, it's human review.

**Struggled with**

- An hour of work went into an approach that was then backed out. The research had the answer all along; the design reached for the most extreme option instead of the most common one.

**Slide material**

- "Do I have to write all the tests first?" No. Lock what's *shipped*, not what's *planned*.
- Factories work where "done" can be said in code. The form changes by domain (scenarios, golden data, screenshots); the principle doesn't.
- Pick high seams and the "is this a refactor or a weakening?" problem mostly disappears.
- Outliers make good headlines and bad defaults: the StrongDM design was the most quotable and the least representative.

## 2026-10-21: Risk gate in shadow mode, and a file-size brake

**What happened**

- Merged the acceptance harness (#13) and the risk gate in shadow mode (#14). The gate labels every PR `risk:low|medium|high` from the files it changes, reads its rules from the base branch, never runs PR code, and blocks nothing yet. Its tier logic is a pure function with 12 unit tests in CI.
- The maintainer raised the self-approval problem: GitHub won't let you approve your own PR, and there is one human. Plan: factory PRs come from a GitHub App identity (so the maintainer approves them normally), and for the maintainer's own PRs the gate accepts a `/approve` comment on the head commit. GitHub's own required-approvals count stays at 0; the `risk-gate` check is the real gate.
- Added Matt Pocock's "prompt of the day" (a public post): fail any file over 5,000 tokens, counted as bytes/4, with instructions for splitting it in the error message. Built it as his pre-commit hook on staged files, and also as part of `./gradlew check`, because factory agents commit in sandboxes and CI runners where local git hooks never run.
- The subscription token for CI can't be created from a phone: `claude setup-token` needs a browser sign-in on the machine running it. The factory waits for the maintainer to be back at a laptop; nothing else is blocked.

**Went well**

- Measuring first: every hand-written file in the repo was already under 5,000 tokens; the largest of Pocock's own skills is about 3,100. The rule costs nothing today and keeps it that way.
- Proving the brake caught a gap: the first version only checked files git already tracked, so a brand-new file slipped past `./gradlew check`. Planting an oversized file found it in a minute.

**Struggled with**

- One person can't be both author and approver on GitHub. Every solo-maintainer factory has to solve this explicitly.
- Remote Control from a phone can't run interactive local commands (a leading `!` goes to the agent as text), so credential setup has to wait for a laptop.

**Slide material**

- "The simplest investment I ever made in my codebase": a 100-line script and a hook.
- Where a rule runs matters: a pre-commit hook protects your laptop; a check in the build protects the factory.
- The bot needs its own identity, or the human can't review it.

## 2026-10-21: Protecting main and building the factory

**What happened**

- The maintainer ran `/install-github-app` at the laptop, which stored the subscription token and added two starter workflows. One of them reviewed every PR automatically with an unpinned marketplace plugin, which would have spent usage on docs-only PRs and duplicated the planned reviewer. Made it opt-in (a `claude-review` label) and restricted `@claude` to collaborators.
- Protected `main` with a ruleset: PRs only, squash merges, `ci` required, no force pushes or deletion, no bypass for anyone. Required approvals stay at 0 because a solo maintainer can't approve their own PRs; the `risk-gate` check takes over that job when it leaves shadow mode. A probe push straight to `main` was rejected.
- The maintainer created the factory's GitHub App (`FACTORY_APP_ID`, `FACTORY_APP_PRIVATE_KEY`): its PRs can be approved by the maintainer, and its pushes trigger CI.
- Built factory v1 on GitHub Actions: a deterministic dispatcher (6 tests), implementer and reviewer prompts, and a publisher that was the only step allowed to write to GitHub.
- Before it ever ran, the maintainer asked for it to run locally instead. Rebuilt it around Matt Pocock's Sandcastle: each ticket gets a Docker sandbox on its own worktree; a script on the maintainer's machine dispatches, runs an implementer and a fresh reviewer session in the sandbox, then pushes and opens the PR as the factory app. Credentials (a Claude token for the sandbox, the app's private key) live in the macOS keychain. ADR 0004 records the design and the Actions version it replaced.
- The hard part was Testcontainers inside a sandbox: tests need Docker, and mounting the host's Docker socket would hand the agent the host's Docker. Each run instead gets a private Docker-in-Docker on its own network. Proved with a probe before writing the orchestrator: the acceptance test started Postgres inside the sandbox and passed.

**Went well**

- Linting the factory before running it: actionlint caught that the `runner` context isn't available in job-level `env`, so every file path in the agent jobs would have been empty on the first run. Both linters, and a type-check of the orchestrator, now run in CI.
- Probing the riskiest assumption first (Testcontainers in a sandbox) before writing 200 lines that depend on it.

**Struggled with**

- Starter templates are written for "works on any repo", not for a factory with a budget: the generated review workflow would have reviewed every PR, unpinned. Read generated config before trusting it.
- Public CI has a non-obvious leak path: uploaded artifacts are downloadable by anyone and are not secret-masked like logs. (Moot once the factory moved local, but worth a slide.)
- Colima only shares the home directory with Docker, so a worktree under `/tmp` mounted as an empty folder.

**Slide material**

- Three trust levels per run: the agent never holds a GitHub credential; deterministic code validates and publishes its work. That held when the factory moved from Actions to a laptop: the harness changed, the brakes didn't.
- "The agent gets its own Docker, not yours."
- The factory needs its own identity, or its PRs can't be approved and don't trigger CI.
- Lint the factory like you lint the app.

## 2026-10-21: Giving the factory a way to ask

**What happened**

- The maintainer asked what happens when an agent isn't sure how to continue. The answer was "decide quietly, or report blocked": no way to ask.
- The research pointed one way. Pocock answers most questions before the factory starts (grilling, `/to-spec`), his triage vocabulary already has `needs-info`, and his Sandcastle loop prompt says to leave a comment on the issue and move on. HumanLayer's 12-factor agents treat contacting a human as a normal action, not an error.
- Added a third outcome, `question`. The rule reuses the PR template's door idea: an agent decides two-way doors itself and records them in the PR body, and stops and asks on one-way doors. Questions are posted as a comment on the ticket, the ticket moves to `needs-info`, and the shift moves on. Answering is a comment plus re-applying `ready-for-agent`; the next run already reads every comment.

**Slide material**

- "When should the agent ask?" has the same answer as "what needs a human's approval?": one-way doors.
- The issue thread is the factory's inbox. No new tool, works from a phone.
