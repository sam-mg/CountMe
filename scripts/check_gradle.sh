#!/usr/bin/env bash
# Formatting, Checkstyle, PMD, SpotBugs, Android lint, unit tests + coverage gate, debug and
# release builds. Same entry point locally (pre-push hook) and in CI.
set -euo pipefail
# shellcheck source=scripts/_common.sh
source "$(dirname "$0")/_common.sh"

./gradlew qualityCheck --console=plain --warning-mode=summary
