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
#   GITHUB_PERSONAL_ACCESS_TOKEN   - voor de GitHub MCP
#   JIRA_USERNAME, JIRA_API_TOKEN  - voor de Jira MCP
#   ANTHROPIC_API_KEY              - alleen voor Claude Code als runtime
#   AGENT_TOKEN_CAP                - harde tokengrens, doorgegeven aan de agent
#   ORCHESTRATOR_MODEL             - optioneel, standaard claude-opus-5
#
# Contract met de agent-runtime (één van drieën moet werken):
#   1. ORCHESTRATOR_RUNNER_CMD wijst naar een CLI die de Claude Code-vlaggen
#      begrijpt (--print, --mcp-config, --append-system-prompt);
#   2. of `claude` (Claude Code CLI) staat op de machine — dit is de standaard
#      en de enige die ANTHROPIC_API_KEY gebruikt;
#   3. of `copilot` (GitHub Copilot CLI) staat op de machine. Die authenticeert
#      op een GitHub-token, niet op een Anthropic-sleutel.
#
# Zolang geen van drieën beschikbaar is, faalt dit script met een duidelijke
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

# Welke agent-runtime draait de keten? Drie mogelijkheden, in deze volgorde:
#
#   1. ORCHESTRATOR_RUNNER_CMD - expliciete override, bijvoorbeeld een
#      self-hosted runner met een eigen CLI. Mag meerdere woorden bevatten
#      ("npx @scope/cli"). Moet de Claude Code-vlaggen begrijpen.
#   2. claude  - Claude Code CLI. Past bij ANTHROPIC_API_KEY.
#   3. copilot - GitHub Copilot CLI. Let op: die authenticeert op een
#      GitHub-token en gebruikt ANTHROPIC_API_KEY niet.
#
# De runtime wordt vóór de secret-check bepaald, want welke secrets nodig zijn
# hangt ervan af.
declare -a RUNNER=()
RUNNER_KIND=""

if [ -n "${ORCHESTRATOR_RUNNER_CMD:-}" ]; then
  # Woordsplitsing is hier gewenst; de variable is een commando, geen pad.
  read -r -a RUNNER <<< "$ORCHESTRATOR_RUNNER_CMD"
  RUNNER_KIND="custom"
  if ! command -v "${RUNNER[0]}" >/dev/null 2>&1; then
    fail "Agent-runtime niet uitvoerbaar" \
      "ORCHESTRATOR_RUNNER_CMD begint met '${RUNNER[0]}' en dat is geen uitvoerbaar commando op deze machine. De variable moet een commando zijn, geen zin."
  fi
elif command -v claude >/dev/null 2>&1; then
  RUNNER=(claude)
  RUNNER_KIND="claude"
elif command -v copilot >/dev/null 2>&1; then
  RUNNER=(copilot)
  RUNNER_KIND="copilot"
else
  fail "Geen agent-runtime gevonden" \
    "Geen 'claude' (Claude Code CLI), geen 'copilot' (GitHub Copilot CLI) en geen ORCHESTRATOR_RUNNER_CMD. Installeer er een op de runner: npm install -g @anthropic-ai/claude-code"
fi

# Vereiste secrets moeten bestaan, ook al staat de waarde niet in de logs.
# Welke dat zijn hangt af van de runtime: de Copilot CLI doet niets met een
# Anthropic-sleutel, en die dan eisen levert een verwarrende fout op.
REQUIRED_VARS=(GITHUB_PERSONAL_ACCESS_TOKEN JIRA_USERNAME JIRA_API_TOKEN)
if [ "$RUNNER_KIND" != "copilot" ]; then
  REQUIRED_VARS+=(ANTHROPIC_API_KEY)
fi
for VAR in "${REQUIRED_VARS[@]}"; do
  if [ -z "${!VAR:-}" ]; then
    fail "Ontbrekende omgevingsvariabele" "$VAR is niet gezet. Zie het env-blok in .github/workflows/agent-delivery.yml. Lokaal: 'set -a && . ./.env.mcp && set +a', plus ANTHROPIC_API_KEY."
  fi
done

# Non-interactieve prompt: alleen het ticket en de harde grenzen. Alle
# gedragsregels (volgorde, escalatie, kill switch, concurrency) staan al in
# het agent-bestand zelf - dit script herhaalt ze bewust niet.
PROMPT="Jira ticket ${ISSUE_KEY}. Voer de volledige keten uit zoals beschreven in .github/agents/orchestrator.agent.md. Tokenplafond voor deze run: ${TOKEN_CAP}. Lever een groene PR met Jira-link op, of escaleer met needs-human en een concrete diagnose."

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

MODEL="${ORCHESTRATOR_MODEL:-claude-opus-5}"

log "Orchestrator starten via: ${RUNNER[*]} (${RUNNER_KIND})"

set +e
case "$RUNNER_KIND" in
  claude|custom)
    # De catalogus zet de agents in .github/agents/ (Copilot/VS Code-conventie).
    # Claude Code zoekt ze in .claude/agents/, dus de orchestrator-definitie
    # gaat hier als systeemprompt mee. Dat start de orchestrator, maar zijn
    # subagents vindt Claude Code zo niet - zie docs/agent-pipeline.md.
    "${RUNNER[@]}" \
      --print \
      --model "$MODEL" \
      --mcp-config "$MCP_CONFIG" \
      --append-system-prompt "$(cat "$AGENT_FILE")" \
      --permission-mode bypassPermissions \
      "$PROMPT"
    ;;
  copilot)
    "${RUNNER[@]}" \
      --agent-file "$AGENT_FILE" \
      --mcp-config "$MCP_CONFIG" \
      --allow-tool 'github' \
      --allow-tool 'atlassian' \
      --prompt "$PROMPT"
    ;;
esac
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
