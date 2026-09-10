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

### Welke agent-runtime draait de keten

Het script kiest in deze volgorde. Is er geen enkele, dan faalt het met een
expliciete melding — het simuleert nooit een geslaagde run.

| # | Runtime | Authenticatie | Wanneer |
| --- | --- | --- | --- |
| 1 | `ORCHESTRATOR_RUNNER_CMD` | afhankelijk van de CLI | Self-hosted runner met een eigen runtime. Moet de Claude Code-vlaggen begrijpen (`--print`, `--mcp-config`, `--append-system-prompt`) |
| 2 | `claude` (Claude Code CLI) | `ANTHROPIC_API_KEY` | Standaard. De workflow installeert hem met `npm install -g @anthropic-ai/claude-code` |
| 3 | `copilot` (GitHub Copilot CLI) | GitHub-token, **niet** de Anthropic-sleutel | Alleen als hij al op de machine staat |

Twee dingen om te weten:

- **Een GitHub-hosted runner heeft geen van beide CLI's aan boord.** Daarom
  installeert de workflow er zelf één. Zet je `ORCHESTRATOR_RUNNER_CMD`, dan
  wordt die stap overgeslagen — dan breng je je eigen runtime mee.
- **`ORCHESTRATOR_RUNNER_CMD` moet een commando zijn, geen zin.** Het eerste
  woord wordt uitgevoerd; het script controleert dat vooraf en zegt het meteen
  als het geen uitvoerbaar commando is.

Het model staat standaard op `claude-opus-5`, te wisselen met repo-variable
`ORCHESTRATOR_MODEL`.

### Openstaand: de subagents worden nog niet gevonden

De catalogus zet alle agents in `.github/agents/*.agent.md` — de conventie van
Copilot en VS Code. **Claude Code zoekt ze in `.claude/agents/`.** De
orchestrator zelf start wél (zijn definitie gaat als systeemprompt mee), maar
zijn subagents — `ticket-refiner`, `domain-implementer`, `pr-gatekeeper` en de
rest — ziet Claude Code op die plek niet staan.

Drie manieren om dat op te lossen:

1. **Spiegel de agents naar `.claude/agents/`** en commit ze. Twee locaties met
   dezelfde inhoud is lelijk, maar het werkt vandaag en beide conventies
   blijven bruikbaar.
2. **Verhuis ze naar `.claude/agents/`** en pas de catalogus aan. Eén bron van
   waarheid, maar de VS Code/Copilot-route vervalt.
3. **Draai op de Copilot CLI**, zoals oorspronkelijk ontworpen. Dan klopt de
   mappenstructuur, maar `ANTHROPIC_API_KEY` doet niets meer en de runner moet
   die CLI hebben.

Dit is een teambesluit, geen bug. Tot het genomen is, doet de orchestrator zijn
werk alleen — hij delegeert niet.

### Lokaal testen

Buiten de workflow, met de juiste variabelen gezet:

```bash
set -a && . ./.env.mcp && set +a
export ANTHROPIC_API_KEY=...
bash scripts/run-orchestrator.sh KAN-42
```

Alleen controleren of de aanroep klopt, zonder een echte agent te starten:

```bash
ORCHESTRATOR_RUNNER_CMD=echo bash scripts/run-orchestrator.sh KAN-42
```

Dat print exact het commando dat de runtime zou krijgen.

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
