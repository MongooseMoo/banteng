#!/usr/bin/env bash
# Runs OpenRewrite recipes against the codebase.
#
# Usage:
#   scripts/rewrite.sh <recipe> [recipe...]           # dry run: writes build/reports/rewrite/rewrite.patch
#   scripts/rewrite.sh --apply <recipe> [recipe...]   # applies changes to the working tree
#   ./gradlew rewriteDiscover --warning-mode summary  # lists all ~2300 available recipes
#
# --warning-mode summary is required because rewrite-gradle-plugin 7.39.0 calls the
# deprecated Project.getProperties, which org.gradle.warning.mode=fail would reject.
# The strict mode stays on for every other build invocation.
set -euo pipefail
cd "$(dirname "$0")/.."

task=rewriteDryRun
if [ "${1:-}" = "--apply" ]; then
  task=rewriteRun
  shift
fi
[ $# -ge 1 ] || { echo "usage: scripts/rewrite.sh [--apply] <recipe> [recipe...]" >&2; exit 2; }

recipes=$(IFS=,; echo "$*")
./gradlew "$task" --warning-mode summary "-Drewrite.activeRecipe=${recipes}"
