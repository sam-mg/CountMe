#!/usr/bin/env bash
# Lints GitHub workflows (actionlint) and every shell script (shellcheck).
set -euo pipefail
# shellcheck source=scripts/_common.sh
source "$(dirname "$0")/_common.sh"

status=0

if require_tool actionlint "brew install actionlint"; then
    actionlint || status=1
fi

if require_tool shellcheck "brew install shellcheck"; then
    shellcheck -x scripts/*.sh .githooks/* || status=1
fi

exit "$status"
