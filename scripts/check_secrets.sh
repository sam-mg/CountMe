#!/usr/bin/env bash
# Refuses commits that contain credentials or signing material.
set -euo pipefail
# shellcheck source=scripts/_common.sh
source "$(dirname "$0")/_common.sh"

status=0

# 1. Files that must never be tracked, whatever they contain.
forbidden="$(git ls-files | grep -E '(^|/)(keystore\.properties|client_secret.*\.json|google-services\.json)$|\.(jks|keystore|p12|pfx)$' || true)"
if [ -n "$forbidden" ]; then
    echo "error: secret-bearing files are tracked by git:" >&2
    echo "$forbidden" >&2
    status=1
fi

# 2. Content scan. gitleaks when present (always in CI), a small pattern net otherwise.
if command -v gitleaks >/dev/null 2>&1; then
    gitleaks detect --source . --config .gitleaks.toml --no-banner --redact --verbose || status=1
else
    if [ "${CI:-}" = "true" ]; then
        echo "error: gitleaks is required in CI." >&2
        exit 1
    fi
    echo "warning: gitleaks not installed (brew install gitleaks); using the basic pattern scan." >&2
    patterns='-----BEGIN [A-Z ]*PRIVATE KEY-----|AIza[0-9A-Za-z_-]{35}|(store|key)Password=[^[:space:]]+'
    hits="$(git grep -nIE "$patterns" -- . ':!keystore.properties.example' ':!scripts/check_secrets.sh' || true)"
    if [ -n "$hits" ]; then
        echo "error: possible secrets found:" >&2
        echo "$hits" >&2
        status=1
    fi
fi

exit "$status"
