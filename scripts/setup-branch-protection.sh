#!/usr/bin/env bash
#
# Branch protection op `main` (fase 0.9).
#
# Zet de poort daadwerkelijk dicht: zonder deze instelling is ci.yml een
# adviesorgaan. Een agent-PR kan dan technisch gemerged worden met rode checks
# en zonder menselijke review - precies wat het plan uitsluit ("PR-review door
# mens, geen auto-merge").
#
# Vereist: GitHub CLI (https://cli.github.com) en **admin** op de repo.
#   gh auth login
#   bash scripts/setup-branch-protection.sh
#
# Alleen tonen wat er zou gebeuren:
#   DRY_RUN=true bash scripts/setup-branch-protection.sh

set -euo pipefail

REPO="${REPO:-SjefKoopmans/haystaq-competence-proj1}"
BRANCH="${BRANCH:-main}"
DRY_RUN="${DRY_RUN:-false}"

# De naam van de verplichte check is bewust de samenvattende job uit ci.yml.
# Komt er later een job bij, dan hoeft deze instelling niet mee te veranderen.
REQUIRED_CHECK="Kwaliteitspoort"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh (GitHub CLI) niet gevonden. Installeer via https://cli.github.com of" >&2
  echo "zet de instellingen met de hand; zie docs/branch-protection.md." >&2
  exit 1
fi

PAYLOAD=$(cat <<JSON
{
  "required_status_checks": {
    "strict": true,
    "checks": [{ "context": "${REQUIRED_CHECK}" }]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "require_code_owner_reviews": true,
    "dismiss_stale_reviews": true,
    "require_last_push_approval": true
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": true,
  "block_creations": false,
  "lock_branch": false,
  "allow_fork_syncing": false
}
JSON
)

echo "Repo:   $REPO"
echo "Branch: $BRANCH"
echo "Check:  $REQUIRED_CHECK"
echo

if [ "$DRY_RUN" = "true" ]; then
  echo "DRY_RUN - dit zou worden verstuurd naar /repos/$REPO/branches/$BRANCH/protection:"
  echo "$PAYLOAD"
  exit 0
fi

printf '%s' "$PAYLOAD" | gh api \
  --method PUT \
  -H "Accept: application/vnd.github+json" \
  "/repos/$REPO/branches/$BRANCH/protection" \
  --input - > /dev/null

echo "Branch protection gezet. Controleren:"
echo "  gh api /repos/$REPO/branches/$BRANCH/protection | jq '{checks: .required_status_checks.checks, reviews: .required_pull_request_reviews}'"
echo
echo "Let op: 'require_last_push_approval' betekent dat een agent die na de"
echo "review nog een commit pusht, opnieuw goedkeuring nodig heeft. Dat is"
echo "bedoeld gedrag: anders kan de bouwketen na de menselijke review nog"
echo "ongezien code toevoegen."
