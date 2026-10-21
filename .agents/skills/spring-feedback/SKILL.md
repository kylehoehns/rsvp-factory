---
name: spring-feedback
description: Gradle commands and test placement for this Spring Boot repo. Use when running tests or checks, when another skill says "typecheck" or "run the tests", or when deciding which kind of test a seam gets.
---

# Spring feedback

The repo's feedback loop is a ladder. Climb it in order and stay on the lowest rung that answers your question; each rung up costs more seconds per run.

| Rung | Command | Answers |
| ---- | ------- | ------- |
| Typecheck | `./gradlew :app:compileJava` | Compiles with Error Prone and NullAway. This is the "typecheck" other skills mention. |
| One test class | `./gradlew :app:test --tests 'dev.rsvpfactory.rsvp.WaitlistTests'` | The slice you are working on. |
| One module | `./gradlew :app:test --tests 'dev.rsvpfactory.rsvp.*'` | Everything a module owns. |
| Done | `./gradlew check` | Format, compile, every test, ArchUnit, Modulith verify. Green here is the completion criterion. |

Formatting is applied for you after each edit. If `check` still reports format violations, run `./gradlew spotlessApply`.

## Where a test goes

A seam (see the `tdd` skill) maps to one of these test types. Pick the lowest one that can observe the behaviour.

- **Rule inside a module, no Spring** (capacity, waitlist order, cutoffs): a plain JUnit test, or a jqwik `@Property` when the rule should hold for every input ("confirmed attendees never exceed capacity"). Milliseconds.
- **A module's API with persistence or events**: `@ApplicationModuleTest` with the Testcontainers Postgres from `TestcontainersConfiguration`. Assert published events with Modulith's `Scenario`. Seconds.
- **HTTP contract**: a web slice with `MockMvcTester` or `RestTestClient`. Seconds.
- **The whole system**: `acceptance/`, which you read but never edit.

Time-dependent rules take a `java.time.Clock`; tests pass `Clock.fixed(...)` and never sleep.

Collaborators inside a module are real objects in tests. Fakes live at the edge only: an adapter for an outside service gets a hand-written fake, or the behavioural twin in `twins/`.

## Checking the tests themselves

`./gradlew :app:pitest -PpitTargets='dev.rsvpfactory.rsvp.*'` mutates the code and reruns the tests. The report is `app/build/reports/pitest/index.html`. A surviving mutant is a behaviour change no test noticed: add the assertion that kills it, at an existing seam.
