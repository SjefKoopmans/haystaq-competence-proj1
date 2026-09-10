#!/usr/bin/env bash
#
# Bouwt het image voor de TijdWijs Testdata MCP (fase 2.3).
#
#   bash scripts/build-testdata-mcp.sh
#
# Daarna kan elke MCP-client de server starten met `docker run -i --rm
# tijdwijs/testdata-mcp:latest` - zie .mcp.json en .vscode/mcp.json.
#
# Rookproef na de build: de server start over stdio en moet antwoorden op een
# `initialize`. Doet hij dat niet, dan is het image kapot en merk je dat hier,
# niet pas als een agent er middenin een run op vastloopt.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${IMAGE:-tijdwijs/testdata-mcp:latest}"
SMOKE="${SMOKE:-true}"

echo "Bouwen: $IMAGE"
docker build -t "$IMAGE" "$REPO_ROOT/tools/testdata-mcp"

if [ "$SMOKE" != "true" ]; then
  echo "Rookproef overgeslagen (SMOKE=$SMOKE)."
  exit 0
fi

echo "Rookproef: initialize over stdio"
REQUEST='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"smoke","version":"0"}}}'

RESPONSE="$(printf '%s\n' "$REQUEST" | docker run -i --rm "$IMAGE" 2>/dev/null | head -n 1 || true)"

if printf '%s' "$RESPONSE" | grep -q '"result"'; then
  echo "OK - server antwoordt op initialize."
else
  echo "FOUT - geen bruikbaar antwoord op initialize. Ontvangen: ${RESPONSE:-<niets>}" >&2
  exit 1
fi
