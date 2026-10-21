@AGENTS.md

## Claude Code specifics

- Skills live in `.agents/skills/` (vendored, see `.agents/skills/VENDORED.md`); `.claude/skills` is a symlink to it. Edit skills there, not through a plugin.
- Hooks in `.claude/hooks/` format Java files after every edit and guard protected paths. When a hook blocks something, its message says why; follow it rather than working around it.
- The `## Agent skills` block lives in `AGENTS.md`. If `/setup-matt-pocock-skills` is re-run, update it there.
