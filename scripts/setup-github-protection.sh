#!/usr/bin/env bash
# Run this once after creating the GitHub repository.
# Requires: gh CLI authenticated with repo admin permissions.
# Usage: bash scripts/setup-github-protection.sh OWNER/REPO

set -euo pipefail

REPO="${1:-}"

if [[ -z "$REPO" ]]; then
  echo "ERROR: Repository name is required."
  echo "Usage: bash scripts/setup-github-protection.sh OWNER/REPO"
  exit 1
fi

FAILED=0

run_cmd() {
  local description="$1"
  shift
  if "$@" 2>&1; then
    echo "PASS: $description"
  else
    echo "FAIL: $description"
    FAILED=1
  fi
}

echo "============================================"
echo "Configuring GitHub repository: $REPO"
echo "============================================"
echo ""

# ── 1. Branch protection: main ─────────────────────────────────────────

echo "── Branch protection: main ──"

run_cmd "Set branch protection for main" \
  gh api --method PUT "repos/$REPO/branches/main/protection" \
    --input - <<'EOF'
{
  "required_status_checks": {
    "strict": true,
    "contexts": [
      "Secret Scan",
      "Backend Build",
      "Backend Test",
      "Backend Lint",
      "Frontend Checks (web-nexus)",
      "Frontend Checks (web-pulse)",
      "Frontend Checks (web-coach)",
      "Frontend Checks (web-arena)"
    ]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true
  },
  "restrictions": null,
  "required_conversation_resolution": true,
  "allow_force_pushes": false,
  "allow_deletions": false
}
EOF

echo ""

# ── 2. Branch protection: develop ───────────────────────────────────────

echo "── Branch protection: develop ──"

run_cmd "Set branch protection for develop" \
  gh api --method PUT "repos/$REPO/branches/develop/protection" \
    --input - <<'EOF'
{
  "required_status_checks": {
    "strict": true,
    "contexts": [
      "Secret Scan",
      "Backend Build",
      "Backend Test",
      "Backend Lint",
      "Frontend Checks (web-nexus)",
      "Frontend Checks (web-pulse)",
      "Frontend Checks (web-coach)",
      "Frontend Checks (web-arena)"
    ]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "dismiss_stale_reviews": true
  },
  "restrictions": null,
  "required_conversation_resolution": true,
  "allow_force_pushes": false,
  "allow_deletions": false
}
EOF

echo ""

# ── 3. Create GitHub environments ───────────────────────────────────────

echo "── GitHub environments ──"

run_cmd "Create staging environment" \
  gh api --method PUT "repos/$REPO/environments/staging" \
    --input - <<'EOF'
{
  "wait_timer": 0,
  "reviewers": [],
  "deployment_branch_policy": null
}
EOF

# Get repo owner for production reviewer
OWNER_ID=$(gh api "users/${REPO%%/*}" --jq '.id' 2>/dev/null || echo "")

if [[ -n "$OWNER_ID" ]]; then
  run_cmd "Create production environment with required reviewer" \
    gh api --method PUT "repos/$REPO/environments/production" \
      --input - <<EOF
{
  "wait_timer": 0,
  "reviewers": [
    {
      "type": "User",
      "id": $OWNER_ID
    }
  ],
  "deployment_branch_policy": null
}
EOF
else
  echo "FAIL: Could not resolve owner ID for ${REPO%%/*}. Create production environment manually."
  FAILED=1
fi

echo ""

# ── 4. Repository settings ─────────────────────────────────────────────

echo "── Repository settings ──"

run_cmd "Configure merge settings (squash only, delete branch on merge)" \
  gh api --method PATCH "repos/$REPO" \
    --input - <<'EOF'
{
  "delete_branch_on_merge": true,
  "allow_squash_merge": true,
  "allow_merge_commit": false,
  "allow_rebase_merge": false,
  "squash_merge_commit_title": "PR_TITLE",
  "squash_merge_commit_message": "PR_BODY"
}
EOF

echo ""

# ── Summary ─────────────────────────────────────────────────────────────

echo "============================================"
if [[ "$FAILED" -eq 0 ]]; then
  echo "ALL PASSED"
else
  echo "SOME COMMANDS FAILED — review output above"
  exit 1
fi
