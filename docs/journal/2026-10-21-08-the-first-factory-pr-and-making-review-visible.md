# 2026-10-21: The first factory PR, and making review visible
**What happened**

- The factory opened its first PR (#20, sign in and start a group) as `rsvp-factory-bot`: implementer about 3 minutes, 22 files, 573 lines; the reviewer added one scenario and left three findings alone with reasons. CI green, risk gate high. The agent's own Merge Danger called it a one-way door (schema, seeded usernames, security rules), which matched the gate's path-based call.
- Reading it by hand found something worth a decision: every GET endpoint is public by default. Fine today, risky as a default later tickets inherit.
- The maintainer couldn't see any review on GitHub, because the factory's reviewer runs before the PR exists and its output was a commit plus a section of the PR body. Now the factory posts the reviewer's findings as a GitHub review from the bot, inline where possible.
- High-risk PRs now get an independent second opinion automatically: the factory works out the tier with the same policy file the risk gate uses, and adds the `claude-review` label, which runs a security-focused review. It started as Opus; the maintainer pushed back on usage, and since most early tickets add migrations (high tier), it runs on Sonnet with a 15-turn cap. Copilot would be more independent (a different vendor); it can replace that step later.

**Slide material**

- First factory PR: bot-authored, red-to-green evidence, and a self-assessed one-way door that agreed with the risk gate.
- Review that fixes things is more useful than review that comments, but it has to be visible to count.
- "Independent" second review means a different job, ideally a different model or vendor, and it has a usage cost worth deciding on purpose.
- The file-size brake fired on this journal, which got split by its own advice.
