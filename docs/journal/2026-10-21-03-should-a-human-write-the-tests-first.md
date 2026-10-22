# 2026-10-21: Should a human write the tests first?
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
