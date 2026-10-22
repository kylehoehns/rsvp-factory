# 2026-10-21: Spec and tickets
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
