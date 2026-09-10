# MCP-koppelingen

> Fase 2 uit [agentic-workflow-plan.md](agentic-workflow-plan.md).
> Vier servers, één secrets-bestand, en een expliciet antwoord op de vraag
> "wat mag deze agent eigenlijk?".

## De vier servers

| # | Naam in `.mcp.json` | Image | Waarvoor | Rechten |
| --- | --- | --- | --- | --- |
| 1 | `jira` | `ghcr.io/sooperset/mcp-atlassian` | Jira lezen en schrijven (status, comments, PR-links) | Browse, Comment, Transition, Edit — **geen** Delete, **geen** Admin |
| 2 | `github` | `ghcr.io/github/github-mcp-server` | Branches, PR's, checks, comments | Classic token, scope `repo` |
| 3 | `postgres` | `crystaldba/postgres-mcp` | Schema-introspectie tijdens implementatie | **read-only**, dubbel afgedwongen |
| 4 | `testdata` | `tijdwijs/testdata-mcp` (lokaal gebouwd) | De toolkit uit opdracht 1 | Schrijven — dit is de tool die data laadt |

Alle vier draaien in Docker over stdio, dus identiek op Windows, macOS en
Linux. Ze zijn gedefinieerd in twee bestanden die inhoudelijk hetzelfde zeggen:

- `.mcp.json` — Claude Code en CI
- `.vscode/mcp.json` — VS Code (Copilot Chat)

Wijzig je de ene, wijzig dan de andere. De servernamen zijn een contract: de
agents in `.github/agents/` verwijzen ernaar.

## Eenmalige setup

```bash
cp .env.mcp.example .env.mcp
```

Vul de waarden in (zie de tabel hieronder), bouw het testdata-image en
herstart je editor volledig:

```bash
bash scripts/build-testdata-mcp.sh
```

| Variabele | Waar haal je die |
| --- | --- |
| `GITHUB_PERSONAL_ACCESS_TOKEN` | <https://github.com/settings/tokens/new> — een **classic** token met scope `repo` |
| `JIRA_USERNAME` | Het e-mailadres waarmee je op Jira inlogt |
| `JIRA_API_TOKEN` | <https://id.atlassian.com/manage-profile/security/api-tokens> |
| `DATABASE_URI` | De read-only rol, zie hieronder |
| `TIJDWIJS_DB_URL` / `TIJDWIJS_API_URL` | Standaardwaarden werken bij een lokale `docker compose up` |

Controleer in Claude Code met `/mcp` of alle vier verbonden zijn.

`.env.mcp` staat in `.gitignore` en wordt nooit gecommit. Iedereen maakt zijn
eigen versie; er is geen gedeeld bestand.

## Least privilege

Het uitgangspunt uit de agent-catalogus: *"Least privilege per agent. Dat
beperkt de schade bij een verkeerde beslissing."* Dat vertaalt zich hier naar
drie concrete maatregelen.

### 1. Jira: een service-account, niet je eigen account

Voor de onbemande pipeline hoort in de secrets het token van een apart account
te staan — voorstel: `svc-tijdwijs-agent`. Redenen:

- In de Jira-historie is zichtbaar wat de agent deed en wat een mens deed.
  Met een persoonlijk token staat er straks bij elke transitie jouw naam.
- Intrekken kan zonder dat iemands eigen werk stilvalt.
- Het account krijgt alleen de permissies die de agent nodig heeft.

Benodigde Jira-permissies op het KAN-project:

| Wel | Niet |
| --- | --- |
| Browse Projects | Delete Issues |
| Add Comments | Administer Projects |
| Transition Issues | Manage Sprints |
| Edit Issues (voor labels en links) | Create Issues *(zie plan §8: geen ticketcreatie door de agent)* |

> **Openstaand (A5 in het plan).** Zolang het service-account er niet is,
> draait de koppeling op een persoonlijk token. Dat werkt, maar de logging
> deugt niet en intrekken raakt een mens. Regel dit vóór fase 4 onbemand gaat
> draaien.

### 2. Postgres: read-only, dubbel afgedwongen

De agent mag het schema lezen om te begrijpen wat er bestaat. Hij heeft geen
enkele reden om te schrijven — daar is de testdata-MCP voor.

Twee onafhankelijke sloten:

1. **De databaserol** (fase 2.4, Persoon A): `tijdwijs_ro` met alleen `SELECT`
   en toegang tot `information_schema`. Dit is het echte slot: het houdt ook
   stand als de MCP-server zelf een bug heeft.
2. **`--access-mode=restricted`** op de server zelf. Dit is het tweede slot,
   niet het eerste.

Bestaat de rol nog niet, dan werkt de koppeling met `tijdwijs:tijdwijs` — maar
dan is er nog maar één slot en dat zit in de laag die je het minst vertrouwt.

### 3. GitHub: geen merge-rechten in de keten

De token heeft `repo`, dus technisch kan hij mergen. Wat dat tegenhoudt is
branch protection (`docs/branch-protection.md`): verplichte review, verplichte
checks, en **Actions mag geen PR's approven**. Zonder die instellingen is de
"geen auto-merge"-afspraak een afspraak in een document, geen grendel.

## Secrets in GitHub Actions

De pipeline uit fase 4 draait zonder `.env.mcp`; die staat alleen op laptops.
Op de runner komen de waarden uit **repository secrets**.

Zet ze eenmalig (vereist admin):

```bash
gh secret set JIRA_USERNAME  --repo SjefKoopmans/haystaq-competence-proj1
gh secret set JIRA_API_TOKEN --repo SjefKoopmans/haystaq-competence-proj1
gh secret set ANTHROPIC_API_KEY --repo SjefKoopmans/haystaq-competence-proj1
```

| Secret | Waarvoor | Opmerking |
| --- | --- | --- |
| `JIRA_USERNAME` | Atlassian MCP | E-mail van het service-account |
| `JIRA_API_TOKEN` | Atlassian MCP | Token van het service-account |
| `ANTHROPIC_API_KEY` | Het model in de keten | Zie kostenplafond in `docs/agent-pipeline.md` |
| `GITHUB_TOKEN` | GitHub MCP | **Niet zelf zetten**: Actions levert deze per run, met exact de rechten uit het `permissions:`-blok |

Repository **variables** (geen secrets, wel schakelaars):

| Variable | Standaard | Betekenis |
| --- | --- | --- |
| `AGENT_ENABLED` | `false` | Kill switch. `false` stopt elke agent-run direct |
| `AGENT_MAX_RUNS_PER_DAY` | `10` | Kostenplafond, hard |
| `RULES_DRIFT_REQUIRED` | `false` | Zet op `true` zodra de rule-registry uit fase 1 er staat |

```bash
gh variable set AGENT_ENABLED --body false --repo SjefKoopmans/haystaq-competence-proj1
```

**Nooit** een token in `.mcp.json`, in een workflow-bestand of in een
Jira-comment. De workflows lezen secrets uitsluitend via `${{ secrets.* }}` en
geven ze door als omgevingsvariabele aan de container, precies zoals
`--env-file` dat lokaal doet.

## Officiële Atlassian MCP (Rovo)

Het plan noemt de officiële Rovo-server. Die draait op OAuth in plaats van op
een API-token en is daardoor prettiger voor mensen, maar lastiger voor een
onbemande runner: OAuth-flows vragen om een browser. De huidige keuze
(`mcp-atlassian` met een API-token) werkt in beide situaties en is daarom
voorlopig de standaard. Gaat de pipeline naar Rovo, dan verandert alleen het
`jira`-blok in de twee mcp-bestanden; de agents merken er niets van.

## Problemen

| Symptoom | Oorzaak |
| --- | --- |
| `docker: --env-file: open .env.mcp: The system cannot find the file specified` | `.env.mcp` ontbreekt — `cp .env.mcp.example .env.mcp` |
| `connection refused` op `host.docker.internal` | Stack draait niet (`docker compose up -d`), of de poort in `.env` wijkt af van die in `.env.mcp` |
| `permission denied for table ...` op de postgres-server | Goed nieuws: de read-only rol werkt. Gebruik de testdata-MCP om te schrijven |
| `tijdwijs/testdata-mcp: image not found` | `bash scripts/build-testdata-mcp.sh` |
| Server verschijnt niet na wijziging in `.mcp.json` | Editor volledig herstarten, niet alleen het venster |
