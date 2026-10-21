# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker.

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`       | Maintainer needs to evaluate this issue  |
| `needs-info`               | `needs-info`         | Waiting on reporter for more information |
| `ready-for-agent`          | `ready-for-agent`    | Fully specified, ready for an AFK agent  |
| `ready-for-human`          | `ready-for-human`    | Requires human implementation            |
| `wontfix`                  | `wontfix`            | Will not be actioned                     |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), use the corresponding label string from this table.

Edit the "Label in our tracker" column to match whatever vocabulary you actually use.

## Factory and risk labels

These sit outside the five triage roles. Skills don't apply them; the factory's workflows do.

| Label | Meaning | Set by |
| ----- | ------- | ------ |
| `spec` | A parent spec issue. Never dispatched. | `/to-spec` (see `issue-tracker.md`) |
| `agent:in-progress` | A factory run has claimed this ticket | Dispatcher |
| `agent:review` | The PR is waiting for the reviewer agent | Implement workflow |
| `agent:blocked` | A run failed, hit the CI-round cap, or conflicted; a comment explains | Any factory workflow |
| `risk:low`, `risk:medium`, `risk:high` | The PR's computed review tier. Display only: the `risk-gate` check is the gate, not the label. | `risk-gate` workflow |
