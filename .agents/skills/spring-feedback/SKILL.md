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
| Done | `./gradlew check` | Format, compile, every test including `acceptance/`, ArchUnit, Modulith verify. Green here is the completion criterion. |

Formatting is applied for you after each edit. If `check` still reports format violations, run `./gradlew spotlessApply`.

## Where a test goes

A seam (see the `tdd` skill) maps to one of these test types. Most of a ticket's tests live in `app/`, because that is where mutation testing can see them.

- **Rule inside a module, no Spring** (capacity, waitlist order, cutoffs): a plain JUnit test, or a jqwik `@Property` when the rule should hold for every input ("confirmed attendees never exceed capacity"). Milliseconds.
- **The HTTP API** (the usual seam for a ticket): a test in `app/src/test`, `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `@Import(TestcontainersConfiguration.class)`, calling the endpoint with `RestTestClient` and asserting on status, JSON and problem `type`. One test per behaviour and per refusal the ticket lists. Seconds.
- **A module's API with persistence or events**: `@ApplicationModuleTest` with the same Testcontainers Postgres. Assert published events with Modulith's `Scenario`.
- **A user journey across the whole system**: one or two new scenario classes in `acceptance/`, extending `AcceptanceScenario` and using `Api` ("a member signs in, starts a group, someone else sees it"). Journeys, not every edge case: those belong in `app/`. Move time with the scenario's `clock`, never with sleeps.

Time-dependent rules take a `java.time.Clock`; tests pass `Clock.fixed(...)` and never sleep.

Collaborators inside a module are real objects in tests. Fakes live at the edge only: an adapter for an outside service gets a hand-written fake, or the behavioural twin in `twins/`.

## Checking the tests themselves

`./gradlew :app:pitest -PpitTargets='dev.rsvpfactory.rsvp.*'` mutates the code and reruns the tests in `app/`. CI does this for every class a PR changes and fails below the mutation threshold, so code with no tests in `app/` fails CI. The report is `app/build/reports/pitest/index.html`. A surviving mutant is a behaviour change no test noticed: add the assertion that kills it, at an existing seam.
