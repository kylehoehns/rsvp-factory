#!/usr/bin/env bash
# PreToolUse guard for file edits.
# Factory runs (RSVP_FACTORY=1) may not touch the paths the risk policy treats as the factory's own
# controls. Interactive sessions get asked instead, because the maintainer edits these on purpose.
set -euo pipefail

input=$(cat)
file=$(jq -r '.tool_input.file_path // .tool_input.notebook_path // empty' <<<"$input")
[[ -z "$file" ]] && exit 0

root=$(git rev-parse --show-toplevel)
rel=${file#"$root"/}

decide() { # decision, reason
  jq -n --arg d "$1" --arg r "$2" \
    '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: $d, permissionDecisionReason: $r}}'
  exit 0
}

case "$rel" in
  acceptance/*)
    reason="acceptance/ is the locked suite. Make the scenario pass by changing the app, not the scenario." ;;
  app/src/main/resources/db/migration/*)
    if git cat-file -e "HEAD:$rel" 2>/dev/null; then
      reason="$rel is already committed, and Flyway migrations are append-only. Add a new V<n>__*.sql instead."
    else
      exit 0
    fi ;;
  .github/* | .factory/* | .agents/* | .claude/* | gradle/* | build-logic/* | *.gradle.kts | gradlew*)
    reason="$rel is part of the factory's own controls (high risk tier). Changes here go through the maintainer." ;;
  *) exit 0 ;;
esac

if [[ "${RSVP_FACTORY:-}" == "1" ]]; then
  decide deny "$reason"
else
  decide ask "$reason"
fi
