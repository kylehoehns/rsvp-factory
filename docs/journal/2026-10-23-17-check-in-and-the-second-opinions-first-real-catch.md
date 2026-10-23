# 2026-10-23: Check-in, and the second opinion's first real catch

**What happened**

- **Feature flags were dropped, check-in was built instead.** Check-in was grilled in three rounds (who checks in, what's counted, the window, undo, who sees attendance) into spec #79 and four tickets, each with a "Not in this ticket" section naming its siblings. All four merged the same afternoon.
- **The grilling caught its own mistake.** Round 2 put a member's check-in on the RSVP endpoint, which would have made `rsvp` and `attendance` depend on each other. Round 3 corrected it to a separate endpoint before any ticket was written.
- **Slicing at seams held.** The second opinion's first comment on #84 was "nothing was added beyond the ticket". Tickets 2–4 ran in parallel after ticket 1.
- **Two of four merged with no human** (#85 undo, #87 follow RSVP changes). #84 needed one decision (the table's shape) and an approval.
- **#86 (attendance) had everything happen to it:**
  1. The second opinion raised a blocker that came from the ticket's own wording (it said signed-out visitors get 403; the app gives 401 everywhere). The ticket was corrected, the factory's fixer declined the blocker with reasons, and the PR went to the maintainer as `decision-needed` rather than blocked (the #71 path, used for real).
  2. Merging `main` into the branch slid new tests between an existing test's `@Test` and its method, so that test silently stopped running. CI stayed green. **The second opinion caught it**: its first correct blocker in about 20 reviews. The factory fixed it in its second round.
  3. It then flagged the response field names as a one-way door; the maintainer accepted them.
- **Approval still needed hand-holding.** Both #84 and #86 had finding threads left open on purpose, and the factory won't merge with open threads, so someone resolved them by hand. Fixed in this PR: an approval of the head commit, with `decision-needed` gone, settles the open finding threads; a thread a person opened still blocks.

**Numbers so far**

- 24 factory PRs: 22 merged (21 as-is), 2 closed unmerged, 0 reverted. Median 0.4 hours from PR to merge.
- The second opinion: two blockers, one mistaken (it missed a triage comment) and one real (the silently disabled test); a few one-way doors, all accepted.

**Struggled with**

- Tickets are the weak link: both of #86's first two stops traced back to a ticket's wording or to where a decision was written down.
- Parallel PRs merge `main` repeatedly, and a mechanical merge can quietly break a test. Nothing deterministic checks that every test still runs.

**Slide material**

- "CI was green, and a test had stopped running. The independent reviewer noticed."
- "Two of four feature PRs merged with no human; the other two stopped for exactly the decisions a human should make."
