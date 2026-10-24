# rsvp-factory

A community events platform (groups, events, RSVPs, waitlists, check-in) built by a software factory: agents implement issues labelled `ready-for-agent`, other agents review them, and a risk gate decides how much human review each PR needs. Spring Boot 4.1, Java 25, Gradle.

## Feedback loop

`./gradlew check` green is the definition of done. For the tight inner loop (one test, one module, formatting, mutation testing) use the `spring-feedback` skill.

Testcontainers needs Docker. Inside the factory sandbox `DOCKER_HOST` is already set; leave it alone. On a Mac using Colima, export these first or every container test fails with "Could not find a valid Docker environment":

```sh
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## Codebase rules the checks can't explain

- Each direct sub-package of `dev.rsvpfactory` is a Spring Modulith module. Types in the module's root package are its public API; everything in sub-packages is internal. Cross a module boundary by calling that API or by publishing an event, never by importing internals.
- Flyway migrations are append-only once they're on `main`: add a new `V<n>__*.sql`. A migration only this branch adds may still be renamed or changed.
- A ticket's tests live in `app/src/test`, where CI mutation-tests the classes a PR changes; `acceptance/` gets one or two end-to-end journeys per ticket (copy `ApplicationScenarios`). Add new scenarios; leave existing ones as they are, because changing one raises the PR's risk tier (ADR 0002).
- `./gradlew check` writes the module structure to `docs/modules/` (gitignored); read it there rather than inferring boundaries from imports.

## Skills and hooks

- Skills live in `.agents/skills/` (vendored, see `.agents/skills/VENDORED.md`); `.claude/skills` is a symlink to it. Edit them there, not through a plugin.
- When a hook in `.claude/hooks/` blocks something, its message says why; follow it rather than working around it.

## Agent skills

### Issue tracker

GitHub Issues on `kylehoehns/rsvp-factory`, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five default triage roles, plus factory and risk labels. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
