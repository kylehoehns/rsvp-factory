# Acceptance scenarios grow with each ticket; changing one raises the risk tier

The `acceptance/` suite runs end-to-end scenarios over HTTP against the real application. Each ticket adds scenarios for the behaviour it delivers, written by the agent that implements it, test-first. Nobody writes the suite ahead of the work.

What the suite protects is the behaviour that has already shipped. A PR that **adds** scenarios is ordinary work. A PR that **changes or deletes** an existing scenario is at least medium risk, so the maintainer always sees it, and one objective check decides whether it goes higher: CI runs the base branch's version of the suite against the PR's application. A genuine refactor of the tests passes, because behaviour didn't change; a failure means the app now behaves differently, and the PR is high risk regardless of what it claims.

Scenarios test at the highest seam, the public HTTP API, so refactoring the code behind it shouldn't touch them. Changes to existing scenarios should be rare, which is why this is enough for now. If they turn out to be common, the next step is a judge agent that reads only the test diff and classifies it as a refactor, a weakening or an intentional behaviour change; like every classifier here it could raise the tier, never lower it.

## Considered options

- **A locked suite written by the maintainer before each ticket, which agents can't change.** This is what StrongDM describes with hidden scenarios. We built it first and backed it out: it makes a human write tests before any work can start, which is closer to waterfall than to how most published factories work (Stripe, Spotify, Vercel, Uber, Shopify, Intercom and Anthropic let agents write tests and catch weak ones with review, mutation testing and risk-tiered human review).
- **No acceptance suite.** Simplest, but then nothing end-to-end protects shipped behaviour from a later agent quietly rewriting it.
