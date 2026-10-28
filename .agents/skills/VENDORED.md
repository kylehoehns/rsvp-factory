# Vendored skills

Most skills here are copied from [mattpocock/skills](https://github.com/mattpocock/skills) (MIT, see `LICENSE-mattpocock-skills`), pinned to one commit:

- Version: 1.3.1
- Commit: `5beb35b040e3de97e54fa9e386d7e42d15c295c2`

`spring-feedback/` is this repo's own skill. Upstream's `code-review` is vendored as `spec-review` (the script renames it and every reference to it), so it doesn't replace Claude Code's built-in `code-review`, which the factory's reviewer also runs.

Why vendor instead of installing the plugin: the plugin updates itself, and a skill is an instruction that runs with the agent's permissions. Pinning makes every update a reviewed diff, and it means sandboxes and CI runners, which don't load your personal skills, see the same set you do.

To update: `scripts/update-skills.sh <commit-sha>`, then read `git diff .agents/skills` before committing. Skills have been renamed between releases (`to-prd` → `to-spec`, `CONTEXT.md` → `GLOSSARY.md`), so check the upstream `CHANGELOG.md` for anything this repo's docs refer to by name.
