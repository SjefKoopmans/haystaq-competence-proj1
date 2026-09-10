#!/usr/bin/env bash
#
# Start de orchestrator-agent voor één Jira-ticket (fase 4.2).
#
# Dit is de seam die .github/workflows/agent-delivery.yml aanroept nadat de
# poortwachter (kill switch, kostenplafond, concurrency) en de applicatiestack
# klaarstaan. Wat de orchestrator daarna doet staat in
# .github/agents/orchestrator.agent.md — dit script weet alleen hoe je hem
# start, welke omgeving hij nodig heeft, en hoe een mislukking eruitziet.
#
# Gebruik:
#   bash scripts/run-orchestrator.sh <ISSUE_KEY>
#
# Vereiste omgevingsvariabelen (zie agent-delivery.yml):
#   ANTHROPIC_API_KEY              - voor de Copilot CLI/agent-runtime
#   GITHUB_PERSONAL_ACCESS_TOKEN   - voor de GitHub MCP
#   JIRA_USERNAME, JIRA_API_TOKEN  - voor de Jira MCP
#   AGENT_TOKEN_CAP                - harde tokengrens, doorgegeven aan de agent
#
# Contract met de agent-runtime (één van beide moet werken):
#   1. de GitHub Copilot CLI is beschikbaar als `copilot` en ondersteunt
#      --agent-file + --prompt voor een non-interactieve run;
#   2. of er bestaat een lokale runner die dezelfde interface biedt
#      (override via ORCHESTRATOR_RUNNER_CMD, zie hieronder).
#
# Zolang geen van beide beschikbaar is, faalt dit script met een duidelijke
# melding in plaats van een agent te simuleren — een orchestrator die niet
# echt draait mag nooit een groene stap lijken.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

log()  { printf '%s\n' "$*"; }
warn() { printf '::warning title=%s::%s\n' "$1" "$2"; }
fail() { printf '::error title=%s::%s\n' "$1" "$2"; exit 1; }

ISSUE_KEY="${1:-}"
if [ -z "$ISSUE_KEY" ]; then
  fail "Geen issue key" "Gebruik: bash scripts/run-orchestrator.sh <ISSUE_KEY>"
fi
if ! printf '%s' "$ISSUE_KEY" | grep -Eq '^KAN-[0-9]+$'; then
  fail "Ongeldige issue key" "'$ISSUE_KEY' voldoet niet aan KAN-<nummer>."
fi

AGENT_FILE="$REPO_ROOT/.github/agents/orchestrator.agent.md"
if [ ! -f "$AGENT_FILE" ]; then
  fail "Orchestrator-definitie ontbreekt" "$AGENT_FILE bestaat niet."
fi

MCP_CONFIG="$REPO_ROOT/.mcp.json"
if [ ! -f "$MCP_CONFIG" ]; then
  fail "MCP-configuratie ontbreekt" "$MCP_CONFIG bestaat niet. Zie docs/mcp-setup.md."
fi

TOKEN_CAP="${AGENT_TOKEN_CAP:-1500000}"
RUN_ID="${GITHUB_RUN_ID:-local-$(date -u +%Y%m%dT%H%M%SZ)}"
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
METRICS_DIR="$REPO_ROOT/docs/agent-metrics"
METRICS_FILE="$METRICS_DIR/${ISSUE_KEY}-${RUN_ID}.json"
mkdir -p "$METRICS_DIR"

log "Ticket: $ISSUE_KEY"
log "Tokencap: $TOKEN_CAP"
log "Agent-definitie: $AGENT_FILE"
log "Metrics-bestand: $METRICS_FILE"

# Vereiste secrets moeten bestaan, ook al staat de waarde niet in de logs.
for VAR in ANTHROPIC_API_KEY GITHUB_PERSONAL_ACCESS_TOKEN JIRA_USERNAME JIRA_API_TOKEN; do
  if [ -z "${!VAR:-}" ]; then
    fail "Ontbrekende omgevingsvariabele" "$VAR is niet gezet. Zie agent-delivery.yml env-block."
  fi
done

# Non-interactieve prompt: alleen het ticket en de harde grenzen. Alle
# gedragsregels (volgorde, escalatie, kill switch, concurrency) staan al in
# het agent-bestand zelf - dit script herhaalt ze bewust niet.
PROMPT="Jira ticket ${ISSUE_KEY}. Voer de volledige keten uit zoals beschreven in .github/agents/orchestrator.agent.md. Tokenplafond voor deze run: ${TOKEN_CAP}. Lever een groene PR met Jira-link op, of escaleer met needs-human en een concrete diagnose."

RUNNER_CMD="${ORCHESTRATOR_RUNNER_CMD:-}"
if [ -z "$RUNNER_CMD" ]; then
  if command -v copilot >/dev/null 2>&1; then
    RUNNER_CMD="copilot"
  else
    fail "Geen agent-runtime gevonden" \
      "Noch 'copilot' (GitHub Copilot CLI) noch ORCHESTRATOR_RUNNER_CMD is beschikbaar. Installeer de Copilot CLI op de runner, of zet ORCHESTRATOR_RUNNER_CMD op een compatibel commando."
  fi
fi

write_metrics() {
  local OUTCOME="$1" ESCALATED="$2" REASON="$3" FINISHED_AT
  FINISHED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  cat > "$METRICS_FILE" <<JSON
{
  "schema_version": 1,
  "issue_key": "${ISSUE_KEY}",
  "ticket_class": "overig",
  "run_id": "${RUN_ID}",
  "started_at": "${STARTED_AT}",
  "finished_at": "${FINISHED_AT}",
  "outcome": "${OUTCOME}",
  "escalated": ${ESCALATED},
  "escalation_reason": ${REASON},
  "retries": 0,
  "ci_first_attempt_green": null
}
JSON
}

log "Orchestrator starten via: $RUNNER_CMD"

set +e
"$RUNNER_CMD" \
  --agent-file "$AGENT_FILE" \
  --mcp-config "$MCP_CONFIG" \
  --allow-tool 'github' \
  --allow-tool 'atlassian' \
  --prompt "$PROMPT"
STATUS=$?
set -e

if [ "$STATUS" -eq 0 ]; then
  log "Orchestrator afgerond."
  write_metrics "in-review" "false" "null"
  exit 0
fi

warn "Orchestrator mislukt" "Exitcode $STATUS. Zie de agent-logs hierboven voor de diagnose."
write_metrics "failed" "true" '"infrastructuur"'
exit "$STATUS"
