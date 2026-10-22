# 2026-10-21: Risk gate in shadow mode, and a file-size brake
**What happened**

- Merged the acceptance harness (#13) and the risk gate in shadow mode (#14). The gate labels every PR `risk:low|medium|high` from the files it changes, reads its rules from the base branch, never runs PR code, and blocks nothing yet. Its tier logic is a pure function with 12 unit tests in CI.
- The maintainer raised the self-approval problem: GitHub won't let you approve your own PR, and there is one human. Plan: factory PRs come from a GitHub App identity (so the maintainer approves them normally), and for the maintainer's own PRs the gate accepts a `/approve` comment on the head commit. GitHub's own required-approvals count stays at 0; the `risk-gate` check is the real gate.
- Added Matt Pocock's "prompt of the day" (a public post): fail any file over 5,000 tokens, counted as bytes/4, with instructions for splitting it in the error message. Built it as his pre-commit hook on staged files, and also as part of `./gradlew check`, because factory agents commit in sandboxes and CI runners where local git hooks never run.
- The subscription token for CI can't be created from a phone: `claude setup-token` needs a browser sign-in on the machine running it. The factory waits for the maintainer to be back at a laptop; nothing else is blocked.

**Went well**

- Measuring first: every hand-written file in the repo was already under 5,000 tokens; the largest of Pocock's own skills is about 3,100. The rule costs nothing today and keeps it that way.
- Proving the brake caught a gap: the first version only checked files git already tracked, so a brand-new file slipped past `./gradlew check`. Planting an oversized file found it in a minute.

**Struggled with**

- One person can't be both author and approver on GitHub. Every solo-maintainer factory has to solve this explicitly.
- Remote Control from a phone can't run interactive local commands (a leading `!` goes to the agent as text), so credential setup has to wait for a laptop.

**Slide material**

- "The simplest investment I ever made in my codebase": a 100-line script and a hook.
- Where a rule runs matters: a pre-commit hook protects your laptop; a check in the build protects the factory.
- The bot needs its own identity, or the human can't review it.
