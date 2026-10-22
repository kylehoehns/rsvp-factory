#!/usr/bin/env bash
# The only factory step that writes to GitHub. It runs with the factory app's token, never with
# an agent, and treats everything the agent produced as data to validate.
#
# Usage: publish.sh <implement|review> <issue-number> [pr-number]
# Env:   GH_TOKEN (factory app), BUNDLE, OUTCOME_FILE, PR_BODY_FILE
set -euo pipefail

role=$1
issue=$2
pr=${3:-}
branch="agent/issue-$issue"
outcome=$(jq -r '.outcome // "blocked"' "$OUTCOME_FILE" 2>/dev/null || echo blocked)
summary=$(jq -r '.summary // "No summary."' "$OUTCOME_FILE" 2>/dev/null || echo "No outcome file.")

block() { # reason
  local target=$issue
  [[ "$role" == review ]] && target=$pr
  gh issue edit "$target" --add-label agent:blocked --remove-label agent:in-progress >/dev/null || true
  # shellcheck disable=SC2016 # the backticks are Markdown in the comment, not command substitution
  gh issue comment "$target" --body "$(printf 'The factory stopped (%s step): %s\n\nA human needs to look. Remove `agent:blocked` and re-apply `ready-for-agent` to retry.' "$role" "$1")"
  exit 0
}

[[ "$outcome" == success ]] || block "$summary"

if [[ -s "$BUNDLE" ]]; then
  git fetch --quiet "$BUNDLE" "HEAD:refs/heads/$branch-incoming"
  # The factory's own controls are off-limits to agents; hooks block this in-session, this is the backstop.
  if git diff --name-only "origin/main...$branch-incoming" | grep -Eq '^(\.github|\.factory|\.agents|\.claude|\.githooks|scripts)/'; then
    block "the branch changes the factory's own controls (.github, .factory, .agents, .claude, .githooks or scripts)."
  fi
  git push --quiet origin "refs/heads/$branch-incoming:refs/heads/$branch"
elif [[ "$role" == implement ]]; then
  block "the agent reported success but made no commits."
fi

body_file=$PR_BODY_FILE
if [[ ! -s "$body_file" ]]; then
  body_file=$(mktemp)
  printf '%s\n\nCloses #%s\n' "$summary" "$issue" >"$body_file"
fi

if [[ "$role" == implement ]]; then
  title=$(gh issue view "$issue" --json title --jq .title)
  gh pr create --draft --base main --head "$branch" --title "$title" --body-file "$body_file" --label agent:review
  gh issue edit "$issue" --remove-label agent:in-progress >/dev/null
else
  gh pr edit "$pr" --body-file "$body_file" --remove-label agent:review >/dev/null
  gh pr ready "$pr"
fi
