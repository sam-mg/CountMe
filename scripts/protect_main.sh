#!/usr/bin/env bash
#
# Configures GitHub so nothing reaches main without passing CI. Needs the GitHub CLI (`gh`),
# logged in as a repository admin. Safe to re-run. It changes repository settings, so it is
# never run automatically: review it, then run it yourself.
#
#   scripts/protect_main.sh [owner/repo]     (default: the origin remote)
set -euo pipefail

repo="${1:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
echo "protecting main and v* tags on $repo"

# main: pull request + passing "CI passed" check + up-to-date branch, applies to admins too.
gh api -X PUT "repos/$repo/branches/main/protection" --input - <<'JSON'
{
  "required_status_checks": { "strict": true, "contexts": ["CI passed"] },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 0,
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true
}
JSON

# Release tags are immutable: they cannot be moved or deleted once created.
gh api -X POST "repos/$repo/rulesets" --input - <<'JSON'
{
  "name": "immutable release tags",
  "target": "tag",
  "enforcement": "active",
  "conditions": { "ref_name": { "include": ["refs/tags/v*"], "exclude": [] } },
  "rules": [{ "type": "deletion" }, { "type": "update" }, { "type": "non_fast_forward" }],
  "bypass_actors": []
}
JSON

echo "done. Pushes to main now need a pull request whose 'CI passed' check is green."
