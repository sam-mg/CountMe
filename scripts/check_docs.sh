#!/usr/bin/env bash
# Validates the published HTML pages and their internal links.
set -euo pipefail
# shellcheck source=scripts/_common.sh
source "$(dirname "$0")/_common.sh"

python3 scripts/check_links.py docs

if require_tool npx "Install Node.js to validate the HTML."; then
    npx --yes html-validate@11 "docs/*.html"
fi
