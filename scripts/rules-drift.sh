#!/usr/bin/env bash
#
# Drift-gate voor de business rules (fase 1).
#
# Werking: regenereer docs/discovered-rules.md uit de domeincode en vergelijk
# met wat er in de repo staat. Wijkt het af, dan is de documentatie achtergelopen
# op de code (of andersom) en faalt de build. Dat is precies het probleem uit de
# nulmeting: docs/business-rules.md week op negen punten af van de code zonder
# dat iemand dat merkte.
#
# Lokaal draaien:  bash scripts/rules-drift.sh
# In CI:           .github/workflows/ci.yml, job `rules-drift`
#
# De generator zelf is scope van Persoon B (fase 1.2). Dit script is het
# koppelvlak: het weet hoe het de generator aanroept en wat een fout betekent.
#
# Contract met de generator (één van beide moet waar zijn):
#   1. backend/pom.xml bevat een Maven-profiel `rules-registry` dat
#      docs/discovered-rules.md schrijft, aanroepbaar met:
#        ./mvnw -B -q -Prules-registry generate-resources
#   2. of er bestaat een scripts/generate-rules.sh dat hetzelfde doet.
#
# Zolang geen van beide bestaat, waarschuwt dit script en slaagt het - tenzij
# RULES_DRIFT_REQUIRED=true. Zet die repo-variable op `true` op het moment dat
# de registry er staat; vanaf dat moment is de gate echt dicht.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

TARGET="docs/discovered-rules.md"
REQUIRED="${RULES_DRIFT_REQUIRED:-false}"

log()  { printf '%s\n' "$*"; }
warn() { printf '::warning title=%s::%s\n' "$1" "$2"; }
fail() { printf '::error title=%s::%s\n' "$1" "$2"; exit 1; }

generator_command() {
  if [ -f backend/pom.xml ] && grep -q "<id>rules-registry</id>" backend/pom.xml; then
    if [ -f backend/mvnw ]; then
      chmod +x backend/mvnw 2>/dev/null || true
      echo "backend-maven-wrapper"
    else
      echo "backend-maven"
    fi
  elif [ -x scripts/generate-rules.sh ] || [ -f scripts/generate-rules.sh ]; then
    echo "script"
  else
    echo "none"
  fi
}

run_generator() {
  case "$1" in
    backend-maven-wrapper) (cd backend && ./mvnw -B -q -Prules-registry generate-resources) ;;
    backend-maven)         (cd backend && mvn    -B -q -Prules-registry generate-resources) ;;
    script)                bash scripts/generate-rules.sh ;;
  esac
}

GENERATOR="$(generator_command)"

if [ "$GENERATOR" = "none" ]; then
  MSG="Rule-registry ontbreekt nog (fase 1.2). Verwacht: Maven-profiel 'rules-registry' in backend/pom.xml of scripts/generate-rules.sh."
  if [ "$REQUIRED" = "true" ]; then
    fail "Drift-gate kan niet draaien" "$MSG"
  fi
  warn "Drift-gate overgeslagen" "$MSG Zet repo-variable RULES_DRIFT_REQUIRED=true zodra de registry er staat."
  exit 0
fi

log "Rule-registry regenereren via: $GENERATOR"

BEFORE=""
if [ -f "$TARGET" ]; then
  BEFORE="$(cat "$TARGET")"
fi

run_generator "$GENERATOR"

if [ ! -f "$TARGET" ]; then
  fail "Drift-gate mislukt" "De generator heeft $TARGET niet geschreven."
fi

AFTER="$(cat "$TARGET")"

if [ "$BEFORE" = "$AFTER" ]; then
  log "Geen drift: $TARGET is actueel."
  exit 0
fi

log "--- verschil ---"
# `diff` faalt met exit 1 bij verschil; dat is hier geen fout maar het antwoord.
diff -u <(printf '%s\n' "$BEFORE") <(printf '%s\n' "$AFTER") || true
log "----------------"

fail "Drift in business rules" \
  "$TARGET loopt niet in de pas met de domeincode. Draai lokaal 'bash scripts/rules-drift.sh', commit het bijgewerkte bestand, en licht in de PR toe welke regel is gewijzigd en waarom het ticket daarom vroeg."
