# TijdWijs Testdata MCP - container

> Fase 2.3 uit [agentic-workflow-plan.md](../../docs/agentic-workflow-plan.md):
> "Testdata-MCP containerizen zodat de runner hem kan starten."

## Waarom een container

De pipeline uit fase 4 draait op een runner die geen Python heeft, geen
virtualenv wil beheren en geen versieconflicten hoort op te lossen. Met een
image start elke agent — lokaal, in CI, op Windows, macOS of Linux — exact
dezelfde server:

```bash
docker run -i --rm --env-file .env.mcp tijdwijs/testdata-mcp:latest
```

MCP loopt hier over **stdio**, niet over HTTP. Er is dus geen poort en geen
service; de client start het proces en praat over stdin/stdout. Daarom is `-i`
verplicht en `-d` zinloos.

## Bouwen en controleren

```bash
bash scripts/build-testdata-mcp.sh
```

Dat bouwt het image én doet een rookproef: de server moet antwoorden op een
`initialize`. In CI draait dezelfde stap (job `mcp-image` in `ci.yml`), zodat
het image niet stilletjes kan verrotten.

## Status van de tools

| Tool | Status | Eigenaar |
| --- | --- | --- |
| `describe_schema(table?)` | **werkt** — leest `information_schema` + `pg_catalog` | infrastructuur |
| `verify_dataset(expectations?)` | **werkt** — telt rijen per tabel en vergelijkt | infrastructuur |
| `reset_environment()` | **werkt** — `POST /api/admin/reset` | infrastructuur |
| `list_business_rules()` | nog niet — komt uit de rule-registry (fase 1.2) | Persoon B |
| `generate_dataset(profile, seed)` | nog niet — opdracht 1 | testdata-toolkit |
| `load_dataset(dataset)` | nog niet — opdracht 1 | testdata-toolkit |

De drie onvoltooide tools bestaan wel en geven een expliciete fout met uitleg.
Dat is bewust: een stub die een leeg resultaat teruggeeft laat een agent
verderbouwen op lucht, en dat kost verderop tienvoudig.

## Invullen door de toolkit uit opdracht 1

De generator hoort in `server.py`, op de plek van de `NotYetImplemented`. Wat
daarbij vast ligt, omdat de agents en de skill `tijdwijs-testdata` erop leunen:

1. **Namen en signatures van de tools blijven zoals ze zijn.** Ze staan in
   `MISSION.md` en in `.github/agents/`.
2. **`generate_dataset` is deterministisch per seed.** Dezelfde seed geeft
   dezelfde dataset — zonder dat is een integratietest niet reproduceerbaar.
3. **`load_dataset` gaat minimaal één keer door de API heen.** Alleen SQL
   inserten slaat de domeinlaag over en levert data op die in de UI niet werkt.
4. **`load_dataset` rapporteert per record: geaccepteerd of afgewezen, met
   reden.** Dat rapport is de input van de feedbackloop.
5. **Niets over het schema wordt hardcoded.** Een kolom erbij mag de generator
   laten falen, maar niet stilletjes.

## Configuratie

| Variabele | Betekenis |
| --- | --- |
| `TIJDWIJS_DB_URL` | Postgres-connectiestring. Schrijfrechten nodig; dit is de tool die data laadt |
| `TIJDWIJS_API_URL` | Basis-URL van de backend, zonder pad. Standaard `http://host.docker.internal:8081` |

`host.docker.internal` verwijst vanuit de container naar de host. Op Linux
werkt dat alleen dankzij `--add-host host.docker.internal:host-gateway`; die
vlag staat daarom in `.mcp.json`.

## Handmatig testen zonder MCP-client

```bash
printf '%s\n%s\n%s\n' \
  '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"cli","version":"0"}}}' \
  '{"jsonrpc":"2.0","method":"notifications/initialized"}' \
  '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"verify_dataset","arguments":{}}}' \
  | docker run -i --rm --add-host host.docker.internal:host-gateway --env-file .env.mcp tijdwijs/testdata-mcp:latest
```

Met een draaiende stack en de minimale seed hoort daar onder meer
`"employee": 2` en `"timesheet": 1` uit te komen.
