# 2026-10-22: The human is the queue

**What happened**

- **First one-way-door decision.** The second opinion on #41 (RSVP to an event, ticket #6) found no blockers, but the factory labelled it `decision-needed`: `rsvp.status` accepted any text, and rows had no timestamp, so waitlist order relied on the row id. Changing that after more features build on the table is expensive. The maintainer chose "merge now, fix next": #41 merged and a follow-up ticket (#45) adds the constraint and a `created_at` column in a new migration.
- **Follow-ups filed themselves.** At merge the factory filed #42, #43 and #44 as `needs-triage`. Triage took a minute: two duplicated tickets already in the spec (cancel an event, change the guest count) and were closed with a note on the ticket they belong to; one became `ready-for-agent`. The factory picked it up within the next poll.
- **Scope leaked between tickets.** #46 (ticket #7, refuse RSVPs over the guest limit or after the cutoff) changed only tests: 49 added lines, no production code. The agent on #41 had already built both refusals. The #7 agent noticed, wrote tests to prove the behaviour, and stopped. Honest, but the ticket boundary didn't hold.
- **A tests-only PR was rated medium.** The risk policy only rates docs as low, and "anything no rule mentions is medium". Nothing is wrong with the gate; the policy just never considered a PR that adds tests and nothing else.
- With the work-in-progress limit at 3, three medium PRs (#46, #47, #48) filled every slot. The factory stopped until the maintainer approved them. Two merged at once; the third was set to merge as soon as its CI passed.

**Numbers so far** (from `docs/factory/metrics.md`)

- 10 factory PRs: 7 merged as-is, 1 approved and waiting on CI (#48), 2 closed unmerged (both early attempts at the first ticket), 0 reverted.
- Median 0.4 hours from PR to merge. The outlier is #41 at 5.1 hours, all of it waiting for one decision.
- 2 to 4 agent sessions and 12k to 72k output tokens per PR.

**Struggled with**

- The bottleneck is no longer the agents. Every slow PR so far was waiting on the maintainer: a decision, or an approval. More agents would only make the queue longer.
- Ticket slicing matters more than it looks. Two neighbouring tickets touching the same rule let one agent build both.

**Slide material**

- "The human is the queue": WIP 3 plus medium-by-default means one person's attention sets the factory's speed.
- One-way doors are the right thing to stop for: the only 5-hour PR was the one with a schema decision in it.
- A follow-up that duplicates an existing ticket is cheap to close; a follow-up nobody files is a bug in production later.
