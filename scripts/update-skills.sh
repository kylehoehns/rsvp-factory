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
cp "$tmp/skills/LICENSE" "$root/.agents/skills/LICENSE-mattpocock-skills"

version=$(jq -r .version "$tmp/skills/.claude-plugin/plugin.json")
sed -i.bak -E "s/^- Commit: .*/- Commit: \`$sha\`/; s/^- Version: .*/- Version: $version/" "$root/.agents/skills/VENDORED.md"
rm "$root/.agents/skills/VENDORED.md.bak"

echo "Vendored mattpocock/skills $version at $sha. Now: git diff .agents/skills"
