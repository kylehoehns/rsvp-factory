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
  .factory/runs/*)
    exit 0 ;; # gitignored scratch space the factory shares with its agents (task, outcome, PR body)
  acceptance/*Scenarios.java)
    if git cat-file -e "HEAD:$rel" 2>/dev/null; then
      reason="$rel is an existing acceptance scenario, which protects shipped behaviour. Add a new scenario class instead; changing this one needs the maintainer (ADR 0002)."
    else
      exit 0
    fi ;;
  app/src/main/resources/db/migration/*)
    # Frozen once it's on main (it may have been applied somewhere); a branch's own new migration can still change.
    if git cat-file -e "origin/main:$rel" 2>/dev/null; then
      reason="$rel is on main, and Flyway migrations are append-only. Add a new V<n>__*.sql instead."
    else
      exit 0
    fi ;;
  .github/* | .factory/* | .agents/* | .claude/* | .githooks/* | scripts/* | gradle/* | build-logic/* | *.gradle.kts | gradlew*)
    reason="$rel is part of the factory's own controls (high risk tier). Changes here go through the maintainer." ;;
  *) exit 0 ;;
esac

if [[ "${RSVP_FACTORY:-}" == "1" ]]; then
  decide deny "$reason"
else
  decide ask "$reason"
fi
