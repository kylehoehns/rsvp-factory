# 2026-10-21: A second opinion, and a brake that was silently off

**What happened**

- Closed the first factory PR and re-ran ticket #3 to see the full pipeline. PR #22 got the factory reviewer's findings posted as a review (three inline comments, each saying what it changed or why it left something), `risk:high`, and the `claude-review` label added automatically.
- The automatic second opinion failed on its first try: `claude-code-action` refuses runs started by a bot unless the bot is listed in `allowed_bots`. A human re-label ran it; the fix names the factory app explicitly.
- The second opinion (Sonnet, security brief) independently flagged "every GET endpoint is public by default", which a human had spotted by hand on the earlier PR, and found something nobody had: demo accounts with a published password, seeded by a versioned migration that would run in production.
- The maintainer asked why the ticket had only acceptance scenarios and no tests in `app/`. The agent had followed the spec exactly (test at the HTTP seam, nothing below it) and put those tests in `acceptance/`. The catch: mutation testing only sees tests in `app/`, so it saw none, and a brake was silently off. Proved it: PIT on #22's changed classes generated 39 mutations and killed 0.
- The retro outcome is guidance plus a check: a ticket's tests live in `app/` (HTTP-seam tests with real Postgres), `acceptance/` gets one or two journeys, and CI now fails a PR whose changed classes score below 60% mutation coverage.

**Went well**

- A cheap, differently briefed second reviewer earned its keep on its first real run, including a finding the first reviewer and a human both missed.
- Proving the new check against the exact code that motivated it: "would this have failed #22?" Yes: 0 of 39 mutants killed.

**Struggled with**

- A brake can be configured, green, and doing nothing. Mutation testing ran on every PR and passed, because it had no tests to judge.

**Slide material**

- "Your brakes can be off while every light is green." Check that each brake actually bites on real code, not just planted defects.
- Two reviewers, two jobs: the factory reviewer fixes against standards; the second opinion only looks for what could go wrong in production.
- The retro loop in one PR: a review comment became a rule and a failing check.
