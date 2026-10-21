# rsvp-factory

A community events platform (groups, events, RSVPs, waitlists, check-in) that is built by a **software factory**: AI agents implement GitHub issues, other agents review them, and a risk gate decides how much human review each change needs.

It's a showcase for what a well-run agent factory looks like at the end of 2026, on Spring Boot 4.1 and Java 25, using [Matt Pocock's skills](https://github.com/mattpocock/skills) for the human-in-the-loop work.

> **Status:** under construction. The brakes (checks) are being built first; the factory gets switched on after them. The git history is meant to be read in order.

## The idea in one line

Build the brakes before the accelerators. Checks, review and codebase design come first; agents that start work on their own come last.

## Running it

Needs JDK 25 and Docker.

```sh
./gradlew check          # everything: format, compile, null-safety, tests, architecture
./gradlew :app:bootRun   # starts Postgres and Grafana LGTM via compose.yaml
```

## License

MIT. Skills in `.agents/skills/` are vendored from mattpocock/skills (MIT); see `.agents/skills/VENDORED.md`.
