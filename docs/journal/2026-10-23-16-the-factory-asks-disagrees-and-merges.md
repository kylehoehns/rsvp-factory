# 2026-10-23: The factory asks, disagrees, and merges

**What happened**

- **Triage by `/triage`.** Four follow-ups went through Pocock's triage skill: each claim checked against the code, then an agent brief or a reasoned close. One "bug" (#53) wasn't one (both paths gave 404) and was folded into #60. One (#39, venues are public) turned out to be a product decision already made, and the real risk behind it, guessable ids, became a spec.
- **Spec #65: ids can't be guessed.** Sequential ids let anyone walk every group and collect every venue. Grilled into ADR 0007 (UUIDv7 primary keys, replacing the numeric ones) and three chained tickets: members, groups, events. All three merged the same afternoon.
- **The factory asked instead of guessing.** The member-id ticket said "no change to existing scenarios", but one scenario hard-coded member ids. The implementer stopped with two questions and its recommended answers (`needs-info`). The maintainer agreed, the factory resumed, and the PR matched the answers.
- **The second opinion's first blocker was wrong.** On #70 it called part of the work "not asked for": it had read the ticket's body, but the triage brief that asked for that work was a comment. The factory's fixer checked, declined with a reason, and changed nothing, which the factory then treated as a failure. Fixed (#71): the second opinion reads comments too, and a declined blocker goes to the maintainer as `decision-needed` instead of `agent:blocked`.
- **Approving now merges.** Approving a medium or high PR used to do nothing until someone clicked merge. The factory now merges once a maintainer has approved the exact head commit and everything is green (#73), including when the second opinion's only finding was a decision the maintainer has since made (#76). #77 was the first: approved, then merged by the factory within a minute.
- **Feature flags were grilled, then dropped.** With no deployment, flipping a checked-in flag file by PR is ceremony. Good for a real product; not for this demo.

**Numbers so far** (from `docs/factory/metrics.md`)

- 20 factory PRs: 18 merged (17 as-is), 2 closed unmerged, 0 reverted. Median 0.4 hours from PR to merge.
- The second opinion, across the PRs where it ran: one blocker (mistaken), one one-way door (accepted as fine), a handful of follow-ups. Still measuring, still unchanged.

**Struggled with**

- Every new rule found its gap on the next real PR: approvals that didn't merge, a "fail" that meant "decided", a ticket criterion that couldn't hold. Each fix was small; none was predictable from the design.
- Ticket text matters as much as code: two of this stretch's stops came from what a ticket said (or where the brief lived), not from the code.

**Slide material**

- "The agent stopped and asked": `needs-info` with options and a recommendation, answered in one line.
- "Two reviewers disagreed, and the right one was the factory." Disagreement goes to a human, not to a retry loop.
- "Approve is the only button the maintainer presses now."
