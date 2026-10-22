# 2026-10-21: The first factory runs (four attempts)
**What happened**

The first ticket (#3, sign in and start a group) took four attempts to reach an agent that was actually working. None of the failures was the agent's.

1. **Bug in the orchestrator.** Sandcastle copies files into the worktree at the same relative path, and the target folder (`.factory/runs/`, gitignored) doesn't exist in a fresh worktree. The run stopped before any agent started, and the factory did what it should: labelled the ticket `agent:blocked` with the exact error as a comment.
2. **Credential mix-up.** The setup script stored a file path in the keychain slot meant for the Claude token. The agent started and reported "Not logged in". Fixed by validating inputs in the setup script and checking the token's shape before the factory claims any ticket.
3. **A dependency quirk.** "Not logged in" again, with a valid token. A direct `docker run` with the same token in the same image worked, which narrowed it down: Sandcastle 0.12.0's `createSandbox()` silently ignores the agent's `env` option (only one-shot `run()` and `interactive()` pass it). The token now goes in the sandbox's environment instead.
4. **Working.** The implementer signed in and started reading the repo the way the prompt asks: AGENTS.md, the acceptance harness, the `spring-feedback` skill.

**Went well**

- The failure path worked from the very first run: a blocked ticket with the reason in a comment, a clean exit, nothing half-pushed.
- Bisecting instead of guessing: "does the token work in this image at all?" took one command and split the problem in two.

**Struggled with**

- Silent failure in a dependency: an option that type-checks and does nothing. Reading the library's source was the only way to find it.
- Every failed attempt left state behind (a claimed label, a worktree, a branch, containers) that had to be cleared before the next try. The factory should clean up after itself on interruption.

**Slide material**

- "Day one of a factory is debugging the factory, not the code."
- Four attempts, zero agent mistakes: the harness is where the bugs were.
- Fail fast on preconditions: check credentials before claiming work.
