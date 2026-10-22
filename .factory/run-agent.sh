#!/usr/bin/env bash
# The runner seam: every runner takes a prompt template and a ticket number, works on the current
# branch, and leaves commits plus $OUTCOME_FILE and $PR_BODY_FILE behind. The workflows never know
# which runner ran; .factory/config.json picks it.
#
# Usage: run-agent.sh <implement|review> <issue-number>
# Env:   TASK_FILE, OUTCOME_FILE, PR_BODY_FILE, USAGE_FILE; a Claude credential.
#
# The full session transcript stays on the throwaway runner. Only usage numbers are written out,
# because this repo is public and uploaded artifacts are downloadable and not secret-masked.
set -euo pipefail

role=$1
issue=$2
root=$(git rev-parse --show-toplevel)
config="$root/.factory/config.json"
runner=$(jq -r .runner "$config")
model=$(jq -r ".${role}Model" "$config")
max_turns=$(jq -r ".${role}MaxTurns" "$config")

prompt=$(sed -e "s#{{TASK_FILE}}#$TASK_FILE#g" -e "s#{{OUTCOME_FILE}}#$OUTCOME_FILE#g" \
  -e "s#{{PR_BODY_FILE}}#$PR_BODY_FILE#g" -e "s#{{ISSUE}}#$issue#g" "$root/.factory/prompts/$role.md")

# Hooks in .claude/settings.json tighten their rules when this is set (see .claude/hooks/).
export RSVP_FACTORY=1

status=0
transcript=$(mktemp)
trap 'rm -f "$transcript"' EXIT

case "$runner" in
  claude-headless)
    # The runner is ephemeral and holds no GitHub write token, so permission prompts are off;
    # the PreToolUse hooks still deny what the factory may not touch.
    claude -p "$prompt" \
      --model "$model" \
      --max-turns "$max_turns" \
      --permission-mode bypassPermissions \
      --output-format stream-json --verbose >"$transcript" || status=$?
    ;;
  *)
    echo "Unknown runner '$runner' in .factory/config.json" >&2
    exit 1
    ;;
esac

# Keep only the numbers: turns, duration, cost and token counts from the final result event.
jq -c 'select(.type == "result") | {role: "'"$role"'", num_turns, duration_ms, total_cost_usd, usage}' \
  "$transcript" >"$USAGE_FILE" || true

# A runner that ends without writing an outcome counts as blocked, never as success.
if [[ ! -s "$OUTCOME_FILE" ]]; then
  jq -n --arg s "The $role session ended (exit $status) without writing an outcome, e.g. at the turn limit." \
    '{outcome: "blocked", summary: $s}' >"$OUTCOME_FILE"
fi
