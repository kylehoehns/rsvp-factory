#!/usr/bin/env bash
# PostToolUse: format the Java file that was just edited, so formatting never reaches review.
set -euo pipefail

file=$(jq -r '.tool_input.file_path // empty')
[[ "$file" == *.java && -f "$file" ]] || exit 0

cd "$(git rev-parse --show-toplevel)"
project=$(cut -d/ -f1 <<<"${file#"$PWD"/}")
[[ -f "$project/build.gradle.kts" ]] || exit 0

# spotlessIdeHook formats one file in about half a second with a warm daemon.
./gradlew -q ":$project:spotlessApply" -PspotlessIdeHook="$file" >/dev/null 2>&1 || true
