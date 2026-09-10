# De agent-pipeline bedienen

> Fase 4 en 5 uit [agentic-workflow-plan.md](agentic-workflow-plan.md), vanuit
> het oogpunt van degene die hem aan- en uitzet.

## Schakelaars

Alles staat in **repository variables** (Settings → Secrets and variables →
Actions → Variables). Variables, geen secrets: het zijn geen geheimen, en je
wilt in de logs kunnen zien welke waarde gold.

| Variable | Standaard | Effect |
| --- | --- | --- |
| `AGENT_ENABLED` | `false` | **Kill switch.** Alles behalve `true` stopt elke run in de eerste stap |
| `AGENT_MAX_RUNS_PER_DAY` | `10` | Kostenplafond. Meer runs vandaag → de run stopt |
| `AGENT_TOKEN_CAP` | `1500000` | Harde tokengrens per run, doorgegeven aan de keten |
| `AGENT_RUNNER` | `ubuntu-latest` | Zet op je self-hosted runner-label zodra die er is |
| `RULES_DRIFT_REQUIRED` | `false` | Maakt de drift-gate in CI bindend (fase 1) |

```bash
gh variable set AGENT_ENABLED --body false --repo SjefKoopmans/haystaq-competence-proj1
```

### De kill switch

```bash
gh variable set AGENT_ENABLED --body false --repo SjefKoopmans/haystaq-competence-proj1
```

Nieuwe runs stoppen daarna in de eerste stap, binnen een minuut. **Een run die
al bezig is, stopt hier niet van.** Die hard afbreken:

```bash
gh run list --workflow agent-delivery.yml --limit 5
gh run cancel <run-id>
```

Draai de kill switch om bij: een agent die een branch vervuilt, onverwachte
kosten, een lek in een prompt, of gewoon twijfel. Terugdraaien kost één
commando; een dag herstelwerk kost meer.

### Kostenplafond

Twee grenzen, want ze vangen verschillende ongelukken op:

- `AGENT_MAX_RUNS_PER_DAY` — vangt een Jira-rule die in een lus zit. De
  workflow telt de runs van vandaag via de Actions-API vóór hij begint.
- `AGENT_TOKEN_CAP` — vangt één run die op hol slaat. De orchestrator moet
  hierop afbreken en dat vastleggen als `escalation_reason: kostenplafond`.

Daarbovenop: `timeout-minutes: 45` op de job, en maximaal twee retries in
`failure-triager`. Vier onafhankelijke grenzen; er is er altijd wel één die
werkt.

> **Openstaand (C1).** Wie krijgt bericht bij 80% van het maandbudget, en wat
> ís het maandbudget? Zonder antwoord is `AGENT_MAX_RUNS_PER_DAY=10` een
> voorzichtige gok, geen onderbouwde grens.

## Een run starten

| Manier | Wanneer |
| --- | --- |
| Label `agent-ready` op een KAN-ticket | Normaal gebruik, zie [jira-automation.md](jira-automation.md) |
| Actions → Agent delivery → Run workflow | Testen, of als de Jira-rule uit staat |
| `gh workflow run agent-delivery.yml -f issue_key=KAN-42` | Vanaf de opdrachtregel |

## Wat de workflow zelf bewaakt

De poortwachter-job doet vier dingen vóór er ook maar iets van een agent
draait, en het zijn precies de dingen die je niet aan een agent overlaat:

1. **Kill switch** — eerste stap, niets ervoor.
2. **Issue key valideren** — de payload komt van buiten. `KAN-<cijfers>` of
   niets. Zo kan een verkeerd geconfigureerde webhook geen willekeurige tekst
   de keten in duwen.
3. **Kostenplafond** — runs van vandaag tellen.
4. **Concurrency per issue key** — twee runs op hetzelfde ticket zouden op
   dezelfde branch schrijven. Een tweede run wacht; hij annuleert de eerste
   niet, want die heeft mogelijk al gepusht.

## `scripts/run-orchestrator.sh`

De seam waar de workflow de ketenlogica aanroept. De workflow zet de omgeving
klaar (stack, MCP-image, secrets, tokencap); dit script start daarna de
`orchestrator`-agent (`.github/agents/orchestrator.agent.md`) met het ticket,
de tokencap en de MCP-configuratie, en schrijft na afloop een metrics-bestand
volgens `docs/agent-metrics/schema.json`.

Vereist op de runner: de GitHub Copilot CLI als `copilot`, of een compatibel
commando via `ORCHESTRATOR_RUNNER_CMD`. Ontbreekt beide, dan faalt het script
met een expliciete melding — het simuleert nooit een geslaagde run.

Lokaal testen (buiten de workflow, met de juiste env-variabelen gezet):

```bash
bash scripts/run-orchestrator.sh KAN-42
```

Zet `AGENT_ENABLED` pas op `true` nadat je dit één keer met een testticket
hebt gedraaid — infrastructuur eerst, dan de keten in het echt aanzetten.

## Als er iets misgaat

| Symptoom | Waar kijken |
| --- | --- |
| Run start niet na een Jira-label | Automation-log in Jira (Rule details → Audit log); daarna de repo-dispatch |
| Run stopt direct met "Kill switch actief" | `AGENT_ENABLED` staat niet op `true` — meestal terecht |
| "Kostenplafond bereikt" | Runs van vandaag tellen; is er een lus in de Jira-rule? |
| Run loopt 45 min en wordt afgebroken | Timeout. Kijk naar `phases[]` in het metrics-bestand: welke fase liep vast? |
| PR heeft label `needs-human` | De keten is gestopt zoals bedoeld. Het PR-comment bevat de diagnose |
| Stack start niet op de runner | Docker beschikbaar? Bij self-hosted: Docker, Java 21, Node en poorten 8081/5433 vrij |

Bij elke mislukte run wordt het metrics-bestand alsnog geschreven en als
artifact bewaard. Zie [agent-metrics/README.md](agent-metrics/README.md).

## Wat nooit automatisch gebeurt

- Merge naar `main` — menselijk besluit, afgedwongen door branch protection
- Deploy
- Ticketcreatie, story points, sprintplanning
- Security- en auth-wijzigingen
- Destructieve datamigraties

Dat zijn geen instellingen maar afspraken uit het plan (§8). De instellingen
die ze afdwingen staan in [branch-protection.md](branch-protection.md).
