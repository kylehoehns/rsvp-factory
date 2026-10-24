# The factory lives in factory-kit; this repo only describes itself

The factory's code moved out of this repo into [factory-kit](https://github.com/kylehoehns/factory-kit), so the same factory can run on other repos, including ones in other languages. That covers the loop, the prompts, the risk gate, the dispatcher and the second-opinion workflow. One local process now watches every repo listed in the kit's `factory.yml`, with one work-in-progress limit shared across them.

What stays here is what only this repo can answer, in two files read from `main`:
- **`.factory/config.yml`**: the check command, maintainers, the factory's bot, protected paths, the sandbox's needs (a private Docker for Testcontainers, a Gradle cache) and the risk tiers.
- **`.factory/Dockerfile`**: the sandbox image.

The gate and the second opinion are reusable workflows this repo calls at `@v1`. Moving to a newer kit version is a PR here, and it is high risk because it touches `.github/`. The prompts no longer name Gradle or this repo's folders: they send agents to `AGENTS.md` for commands, test locations and which files are numbered.

The kit is TypeScript only: the Python gate and dispatcher were ported with their tests. Someone adopting it shouldn't need Python as well as Node. The policy became YAML so it can carry comments and has no tie to GitHub's folder.

## Consequences

- A fix to the gate or the loop lands once, in the kit, for every repo.
- This repo's CI no longer tests the factory; the kit's CI does.
- The factory doesn't work on the kit itself. A factory changing its own gate is the riskiest loop there is, so kit changes are made by hand and merged by the maintainer.
- Agents' hooks check `FACTORY_RUN=1` (it was `RSVP_FACTORY=1`).
