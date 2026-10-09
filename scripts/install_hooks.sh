#!/usr/bin/env bash
# Points git at the versioned hooks in .githooks (pre-push runs the quality gates).
set -euo pipefail
# shellcheck source=scripts/_common.sh
source "$(dirname "$0")/_common.sh"

git config core.hooksPath .githooks
echo "hooks installed: git now uses .githooks (pre-push runs scripts/preflight.sh)"
