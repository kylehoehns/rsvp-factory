# 2026-10-21: Giving the factory a way to ask
**What happened**

- The maintainer asked what happens when an agent isn't sure how to continue. The answer was "decide quietly, or report blocked": no way to ask.
- The research pointed one way. Pocock answers most questions before the factory starts (grilling, `/to-spec`), his triage vocabulary already has `needs-info`, and his Sandcastle loop prompt says to leave a comment on the issue and move on. HumanLayer's 12-factor agents treat contacting a human as a normal action, not an error.
- Added a third outcome, `question`. The rule reuses the PR template's door idea: an agent decides two-way doors itself and records them in the PR body, and stops and asks on one-way doors. Questions are posted as a comment on the ticket, the ticket moves to `needs-info`, and the shift moves on. Answering is a comment plus re-applying `ready-for-agent`; the next run already reads every comment.

**Slide material**

- "When should the agent ask?" has the same answer as "what needs a human's approval?": one-way doors.
- The issue thread is the factory's inbox. No new tool, works from a phone.
