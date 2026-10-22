# 2026-10-22: Cross-checking against the consensus, and triaging findings

**What happened**

- The maintainer asked the question every overnight factory hits: if a PR gets stuck on review findings (here, a password column width), does nothing else get built? And who decides what becomes a follow-up ticket?
- Before changing anything, two research passes cross-checked the factory against what others do: one on review-finding triage, one on stacking and auto-merge. The cross-check found the foundation sound and the gaps in flow (one PR at a time, every finding stalls the PR) and measurement (nothing measured).
- Agreed plan: findings triage, measurement, work in progress up to 3, a seeded `CODING_STANDARDS.md`, and staged auto-merge. Stacked PRs were skipped for now; the research ranked them riskiest (rework cascades) and least needed (with #3 merged, three tickets are independent).
- Built findings triage (ADR 0005): blocker / one-way door / follow-up / nit, at most two automatic review-fix rounds, follow-ups filed at merge, nits dropped. The maintainer asked for one more thing: reply to each review thread with how it was handled and resolve it, so the open conversations on a PR are exactly what's left for a human.

**Went well**

- Asking the research before building: every shipping review tool ranks findings and none blocks by default, so "what blocks" was ours to design; nobody files follow-ups automatically, so the guardrails came from two community designs.

**Slide material**

- "A reviewer prompted to find gaps will usually report some." So decide what blocks on purpose.
- Open conversations on a PR = the human's to-do list. Everything else is answered and resolved by the factory.
- Cross-check your factory against the field before adding accelerators.
