#!/usr/bin/env bash
#
# Runs every quality gate locally, in the same order and with the same scripts the pre-push hook
# and CI use. Slowest gate (Gradle) last so cheap failures show up first.
#
#   scripts/preflight.sh            all gates
#   scripts/preflight.sh docs       only the named gates: secrets | workflows | docs | gradle
set -uo pipefail
# shellcheck source=scripts/_common.sh
source "$(dirname "$0")/_common.sh"

bold=""; red=""; green=""; reset=""
if [ -t 1 ]; then
    bold="$(printf '\033[1m')"; red="$(printf '\033[31m')"
    green="$(printf '\033[32m')"; reset="$(printf '\033[0m')"
fi

gates=("$@")
if [ "${#gates[@]}" -eq 0 ]; then
    gates=(secrets workflows docs gradle)
fi

failed=0
total="${#gates[@]}"
index=0
for gate in "${gates[@]}"; do
    index=$((index + 1))
    script="scripts/check_${gate}.sh"
    if [ ! -x "$script" ]; then
        echo "unknown gate: $gate" >&2
        exit 2
    fi
    printf '%s[%d/%d]%s %s\n' "$bold" "$index" "$total" "$reset" "$gate"
    if "$script"; then
        printf '      %sok%s\n' "$green" "$reset"
    else
        printf '      %sfailed%s\n' "$red" "$reset"
        failed=1
    fi
done

if [ "$failed" -ne 0 ]; then
    printf '\n%sone or more gates failed.%s\n' "$red" "$reset"
    exit 1
fi
printf '\n%sall gates passed.%s\n' "$green" "$reset"
