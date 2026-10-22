# Coding standards

Judgment calls the reviewer checks. Anything a tool can check is a check instead (formatting, null-safety, module boundaries, time from a `Clock`, file size): those fail the build, so they aren't repeated here. Every rule below came from a review finding; add one through `/retro` when the same comment shows up twice.

## Security

- **Closed by default.** Every endpoint requires a signed-in member unless it's on an explicit list of public routes. A new public route is a decision, written down where the list lives, never a side effect of a broad pattern like `GET /**`. *(#20, #22)*
- **Public responses show the minimum about members.** A response anyone can read carries only what that screen needs; prefer display names over login names, and never contact details. *(#26)*
- **Refusals don't leak.** An unknown member and a wrong password get the same response; errors never carry stack traces or internal ids beyond the resource asked for. *(#22, #26)*

## Data and schema

- **The database generates ids.** No code, seed or fixture chooses an id; refer to seeded rows by a natural key such as a username. *(#26)*
- **Demo and test data stay out of production.** They live in a Flyway location only the dev and test configurations include, never in `db/migration`. *(#22)*
- **Size columns for the next step you can already see.** If a value's format is likely to change (a password hash's algorithm, an external id's length), choose a type that survives that change without another migration. *(#26)*

## Shape of the code

- **One place builds each refusal.** A rule's problem detail (its `type`, status and wording) is created in one place and reused, not rebuilt in each controller. *(#26)*
