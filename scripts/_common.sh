#!/usr/bin/env bash
# Shared helpers for the quality-gate scripts. Source it, do not execute it.

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root" || exit 1

# In CI a missing tool is a failure; on a laptop it is a warning so the hook stays usable.
require_tool() {
    local tool="$1" hint="$2"
    if command -v "$tool" >/dev/null 2>&1; then
        return 0
    fi
    if [ "${CI:-}" = "true" ]; then
        echo "error: '$tool' is required in CI. $hint" >&2
        exit 1
    fi
    echo "warning: '$tool' not installed, skipping. $hint" >&2
    return 1
}
