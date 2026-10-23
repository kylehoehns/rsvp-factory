# Review findings block a merge only when they must

Every finding from the factory's reviewer and the second opinion is one of four kinds. A **blocker** is introduced by the PR and is a correctness bug, a security problem with a demonstrated path, or a spec mismatch; it is fixed before merge, by the reviewer itself or in at most two automatic review-fix rounds, after which the PR goes to the maintainer. If the fixer checks a blocker and declines it with a reason, and changes nothing, the two reviewers disagree: the PR is labelled `decision-needed` with both sides on the thread, rather than blocked. A **one-way door** (schema, public API, data that could be lost) the ticket didn't settle is labelled `decision-needed` and left open for the maintainer. A **follow-up** is real but not this PR's to fix; at most three per PR are listed on it and filed as `needs-triage` tickets when it merges. A **nit** is never recorded: the reviewer fixes it in passing or drops it. The factory replies to each finding's review thread with what it did and resolves it, except one-way doors, so a PR's open conversations are exactly what the maintainer still has to decide.

We chose this because unfiltered AI review findings are mostly not acted on (0.9–19.2% across 22,326 comments in one 2025 study, against about 60% for human comments), and a reviewer asked to find gaps will usually report some even when the work is sound; if every finding blocked, every PR would stall. Shipping review tools all rank findings by severity and none blocks by default, so what blocks is ours to decide. The two-round cap mirrors the CI-round cap (Stripe) and Cursor Bugbot Autofix's attempt limit.

## Consequences

- Filing follow-ups automatically has no off-the-shelf equivalent; whether they get done is worth measuring.
- A recurring kind of finding should become a check or a `CODING_STANDARDS.md` line through `/retro`, not a recurring follow-up.
