#!/usr/bin/env bash
# Re-vendor mattpocock/skills at a given commit. Review the diff before committing:
# skills are instructions that run with the agent's permissions, so they get the same review as code.
#
# Usage: scripts/update-skills.sh <commit-sha>
set -euo pipefail

sha=${1:?usage: scripts/update-skills.sh <commit-sha>}
root=$(git rev-parse --show-toplevel)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

git clone --quiet https://github.com/mattpocock/skills "$tmp/skills"
git -C "$tmp/skills" checkout --quiet "$sha"

# The plugin manifest lists the promoted skills; in-progress and misc skills stay out.
for path in $(jq -r '.skills[]' "$tmp/skills/.claude-plugin/plugin.json"); do
  name=$(basename "$path")
  rm -rf "$root/.agents/skills/$name"
  cp -R "$tmp/skills/$path" "$root/.agents/skills/$name"
done
# Renamed on the way in. A vendored skill with the name of a Claude Code built-in replaces it, and the factory's
# reviewer runs the built-in code-review (bugs) and security-review as well as this one (ticket and standards).
for pair in code-review:spec-review; do
  from=${pair%%:*} to=${pair##*:}
  rm -rf "$root/.agents/skills/$to"
  mv "$root/.agents/skills/$from" "$root/.agents/skills/$to"
  for file in $(grep -rl -- "$from" "$root/.agents/skills"); do
    sed -i.bak -E "s/(^|[^a-z-])$from([^a-z-]|\$)/\1$to\2/g" "$file" && rm "$file.bak"
  done
done

cp "$tmp/skills/LICENSE" "$root/.agents/skills/LICENSE-mattpocock-skills"

version=$(jq -r .version "$tmp/skills/.claude-plugin/plugin.json")
sed -i.bak -E "s/^- Commit: .*/- Commit: \`$sha\`/; s/^- Version: .*/- Version: $version/" "$root/.agents/skills/VENDORED.md"
rm "$root/.agents/skills/VENDORED.md.bak"

echo "Vendored mattpocock/skills $version at $sha. Now: git diff .agents/skills"
