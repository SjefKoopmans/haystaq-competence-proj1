#!/usr/bin/env bash
#
# Labels die de agents en workflows gebruiken (fase 3/4).
#
#   bash scripts/setup-labels.sh
#
# Vereist GitHub CLI en schrijfrechten op de repo. Bestaat een label al, dan
# wordt kleur en omschrijving bijgewerkt; er gaat niets verloren.
#
# Zonder deze labels faalt `gh pr edit --add-label` in de workflows, en dat
# gebeurt precies op het moment dat je het niet wilt: bij een escalatie.

set -euo pipefail

REPO="${REPO:-SjefKoopmans/haystaq-competence-proj1}"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh (GitHub CLI) niet gevonden. Installeer via https://cli.github.com" >&2
  exit 1
fi

label() {
  local name="$1" color="$2" description="$3"
  if gh label create "$name" --repo "$REPO" --color "$color" --description "$description" 2>/dev/null; then
    echo "aangemaakt: $name"
  else
    gh label edit "$name" --repo "$REPO" --color "$color" --description "$description" >/dev/null
    echo "bijgewerkt:  $name"
  fi
}

label agent-generated       "5319e7" "Een agent heeft aan deze PR meegeschreven. Altijd zetten (advies C4)."
label needs-human           "d93f0b" "De keten is gestopt en heeft een besluit van een mens nodig."
label needs-migration-review "fbca04" "Raakt db/migration. Forward-only en onomkeerbaar; extra scherp reviewen."
label agent-ready           "0e8a16" "Trigger voor de pipeline. Jira Automation zet dit; de rule haalt het er weer af."

echo
echo "Klaar. Controleren: gh label list --repo $REPO"
