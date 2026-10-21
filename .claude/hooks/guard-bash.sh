#!/usr/bin/env bash
# PreToolUse guard for shell commands: keep builds on the wrapper and keep git history honest.
set -euo pipefail

cmd=$(jq -r '.tool_input.command // empty')

deny() {
  jq -n --arg r "$1" \
    '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: "deny", permissionDecisionReason: $r}}'
  exit 0
}

# Word-boundary match on the command text; good enough for steering, not a security boundary.
if grep -Eq '(^|[;&|[:space:]])mvn([[:space:]]|$)' <<<"$cmd"; then
  deny "This is a Gradle build. Use ./gradlew (see the spring-feedback skill)."
fi
if grep -Eq '(^|[;&|[:space:]])gradle([[:space:]]|$)' <<<"$cmd"; then
  deny "Use ./gradlew so the pinned Gradle version runs, not a system gradle."
fi
if grep -Eq -- '--no-verify|--force([[:space:]]|$)|push[[:space:]].*-f([[:space:]]|$)' <<<"$cmd"; then
  deny "Skipping hooks and force-pushing are off-limits. Fix the failing check instead."
fi
if [[ "${RSVP_FACTORY:-}" == "1" ]] && grep -Eq '(^|[;&|[:space:]])git[[:space:]]+(push|remote)' <<<"$cmd"; then
  deny "Factory runs never push. Commit locally; the publish job pushes after validating the bundle."
fi
exit 0
