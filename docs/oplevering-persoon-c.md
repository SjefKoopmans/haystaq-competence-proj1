# Oplevering Persoon C — platform, CI en integraties

> Branch `PersoonC`. Hoort bij [agentic-workflow-plan.md](agentic-workflow-plan.md)
> en [agent-catalogus.md](agent-catalogus.md).

## Wat er af is

### Fase 0 — Fundering (blok 6 t/m 9)

| Onderdeel | Waar |
| --- | --- |
| Frontend-toolchain: vitest, testing-library, eslint 9 (flat), prettier, `tsc --noEmit` | `frontend/` |
| Eén poort voor de frontend: `npm run verify` | `frontend/package.json` |
| Negen tests op `api.ts` en `DataTable` | `frontend/src/**/*.test.ts(x)` |
| `.editorconfig` | repo-root |
| `CODEOWNERS` | `.github/CODEOWNERS` |
| PR-template met agent-checklist | `.github/pull_request_template.md` |
| CI-workflow | `.github/workflows/ci.yml` |
| Branch protection | `scripts/setup-branch-protection.sh` + [branch-protection.md](branch-protection.md) |

De tests op `api.ts` zijn karakteriseringstests: ze leggen vast dat de frontend
`payload.error` leest. Gaat het foutcontract in fase 1 naar RFC 9457, dan vallen
ze om — precies zoals bedoeld, want dan breekt de frontend mee (openstaand punt
D5 is daarmee beantwoord: **ja**, de frontend parseert het huidige formaat).

### Fase 1 — CI-stap voor de drift-gate

`scripts/rules-drift.sh` regenereert `docs/discovered-rules.md` en faalt bij
verschil. Job `rules-drift` in `ci.yml` draait hem.

Zolang de rule-registry er niet is, **waarschuwt** de stap in plaats van te
falen. Zet repo-variable `RULES_DRIFT_REQUIRED=true` zodra fase 1.2 er staat;
vanaf dat moment is de gate bindend. Het script verwacht één van beide:

- een Maven-profiel `rules-registry` in `backend/pom.xml`, aanroepbaar met
  `./mvnw -B -q -Prules-registry generate-resources`, of
- `scripts/generate-rules.sh`.

### Fase 2 — MCP-koppelingen

Vier servers in `.mcp.json` en `.vscode/mcp.json`: `github`, `jira`, `postgres`
(read-only) en `testdata`. Rechten, secrets en het Jira-service-account staan in
[mcp-setup.md](mcp-setup.md).

De testdata-MCP is gecontaineriseerd (`tools/testdata-mcp/`). `describe_schema`,
`verify_dataset` en `reset_environment` werken en zijn getest tegen de draaiende
stack; `generate_dataset`, `load_dataset` en `list_business_rules` bestaan wel
maar geven een expliciete "nog niet geïmplementeerd" — dat is opdracht 1.
`bash scripts/build-testdata-mcp.sh` bouwt het image en doet een rookproef; CI
draait dezelfde stap.

### Fase 3 — Vier agents

`.github/agents/`: `pr-author`, `failure-triager`, `jira-scribe`,
`metrics-collector`. Elk met input/output-contract, harde regels en
escalatiepad.

`jira-scribe` bevat de echte statusnamen en -ids van het bord KAN, opgehaald bij
de site: `To Do` (10004), `In Progress` (10005), `In Review` (10006), `Done`
(10007), gelijk voor alle issuetypes. Daarmee is openstaand punt **A2**
beantwoord.

### Fase 4 — Trigger en orkestratie

`.github/workflows/agent-delivery.yml` met vier onafhankelijke grenzen:

1. Kill switch `AGENT_ENABLED` — eerste stap, effect binnen een minuut
2. Kostenplafond `AGENT_MAX_RUNS_PER_DAY` + tokencap `AGENT_TOKEN_CAP`
3. `timeout-minutes: 45`
4. Concurrency per issue key

Plus payloadvalidatie: de issue key uit de webhook moet `KAN-<cijfers>` zijn.
De payload komt van buiten en wordt niet vertrouwd.

De Jira-kant staat in [jira-automation.md](jira-automation.md), het bedienen in
[agent-pipeline.md](agent-pipeline.md).

### Fase 5 — Metrics-rollup

`docs/agent-metrics/schema.json` (contract), `scripts/metrics-rollup.mjs`
(validator + rapportgenerator, zonder dependencies) en
`.github/workflows/agent-report.yml` (wekelijks, opent een PR).

Het rapport begint met de twee metrics waar besluiten aan hangen: runs zonder
menselijke correctie, en de false negatives/positives van `pr-gatekeeper`.

## Wat een mens nog moet doen

| # | Actie | Rechten nodig | Blokkeert |
| --- | --- | --- | --- |
| 1 | `scripts/setup-branch-protection.sh` draaien, **nadat** `ci.yml` één keer op `main` heeft gedraaid | GitHub-admin | Fase 0.9 |
| 2 | Secrets zetten: `JIRA_USERNAME`, `JIRA_API_TOKEN`, `ANTHROPIC_API_KEY` | GitHub-admin | Fase 4 |
| 3 | Variables zetten: `AGENT_ENABLED=false`, `AGENT_MAX_RUNS_PER_DAY`, `RULES_DRIFT_REQUIRED=false` | GitHub-admin | Fase 4 |
| 4 | `scripts/setup-labels.sh` draaien (`agent-generated`, `needs-human`, `needs-migration-review`, `agent-ready`) | GitHub-write | Fase 3/4 |
| 5 | Jira-service-account `svc-tijdwijs-agent` + API-token | Jira-admin | A5 |
| 6 | Jira Automation-rule aanmaken | Jira-projectadmin | A4, fase 4.1 |
| 7 | Besluit self-hosted runner, daarna `AGENT_RUNNER` zetten | — | B5 |

Punt 1 tot en met 4 zijn in tien minuten gedaan; 5 tot en met 7 zijn besluiten,
geen werk.

## Afhankelijk van anderen

| Wat | Van wie | Gevolg zolang het er niet is |
| --- | --- | --- |
| Maven wrapper + spotless/checkstyle/jacoco in `pom.xml` | Persoon A | CI valt terug op de Maven van de runner en slaat de kwaliteitsstappen over met een `::notice` |
| Rule-registry (fase 1.2) | Persoon B | Drift-gate waarschuwt in plaats van te falen |
| Read-only rol `tijdwijs_ro` (fase 2.4) | Persoon A | Postgres-MCP draait op één slot in plaats van twee |
| Generator en loader in de testdata-MCP | opdracht 1 | Drie tools geven een expliciete foutmelding |
| `scripts/run-orchestrator.sh` (fase 4.2) | Persoon B | `agent-delivery.yml` stopt met een duidelijke melding |

Geen van deze afhankelijkheden breekt iets; ze staan allemaal als expliciete
waarschuwing of foutmelding in de betreffende stap. Dat is bewust: een gate die
stilzwijgend niets doet is erger dan een gate die zegt dat hij niets doet.

## Bekende punten

- **npm audit meldt drie high-severity issues in `react-router-dom` 6.28.0.**
  Dat is een runtime-dependency van de applicatie, geen onderdeel van de
  toolchain; een upgrade hoort in een eigen ticket met een eigen test, niet
  verstopt in een CI-PR.
- **`npm run format:check` staat niet in `verify` en niet in CI.** De bestaande
  bronbestanden zijn nooit door Prettier gegaan; die nu allemaal herformatteren
  zou de echte wijzigingen in elke volgende diff verbergen. `npm run format` is
  er wel, voor nieuwe bestanden.
- **`enforce_admins` staat uit in de branch protection.** Bij een vastgelopen
  pilot moet een mens er nog bij kunnen. Zet het aan zodra de pipeline draait.

## Verificatie

```bash
cd frontend && npm ci && npm run verify
```

```bash
bash scripts/build-testdata-mcp.sh
```

```bash
bash scripts/rules-drift.sh
```

```bash
node scripts/metrics-rollup.mjs --validate
```

Alle vier zijn lokaal groen gedraaid; de eerste drie draaien ook in `ci.yml`.
