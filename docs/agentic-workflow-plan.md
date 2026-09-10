# Plan: Jira-naar-PR agentic delivery pipeline (TijdWijs)

> Opgesteld 2026-09-10. Status: concept, wacht op input (zie §10).
> Jira-site: <https://haystaqteam3.atlassian.net/>
> Zie ook: [agent-catalogus.md](agent-catalogus.md) voor de gedetailleerde
> onderverdeling van agents, skills en instructions.

## 1. Besluiten (vastgelegd met opdrachtgever)

| Onderwerp | Besluit |
| --- | --- |
| Laatste menselijk checkpoint | PR-review door mens. **Geen auto-merge.** |
| Jira-rechten agent | Lezen + schrijven van status/comments/PR-links. Geen ticketcreatie of sprintbeheer. |
| Jira MCP | Officiële Atlassian MCP (Rovo) |
| Runtime | GitHub Actions op self-hosted runner, getriggerd door Jira-webhook |
| Relatie opdracht 1 | Testdata-toolkit is een **bouwsteen**; de pipeline gebruikt hem |
| Doelgroep plan | Technisch plan + businesscase |

## 2. Repo-nulmeting (feitelijk vastgesteld)

**Ontbreekt volledig:**

- CI/CD: geen `.github/`, geen workflows, geen GitLab/Jenkins/Azure
- Agent-customization: geen `copilot-instructions.md`, `AGENTS.md`, `*.agent.md`,
  `*.instructions.md`, `*.prompt.md`, `.github/skills/`, `mcp.json`
- Tests: **geen `backend/src/test`**. `pom.xml` heeft alleen
  `spring-boot-starter-test`. Geen Testcontainers, RestAssured, jacoco,
  surefire/failsafe-config, checkstyle, spotless, spotbugs. Geen Maven wrapper.
- Frontend: geen vitest, playwright, eslint, prettier, testing-library.
  Scripts: alleen `dev`/`build`/`preview`.
- Geen `.editorconfig`, `CODEOWNERS`, PR-template, issue-templates

**Aanwezig:**

- Spring Boot 3.4.1, Java 21. Flyway `V1__schema.sql` + `V2__seed.sql`
- `POST /api/admin/reset` → `flyway.clean()` + `migrate()`
- docker-compose: db (postgres:16-alpine, :5433), backend (:8081, healthcheck),
  frontend (:3001, géén healthcheck)
- Feature flag `tijdwijs.debug-rules` via `DEBUG_RULES` → logt rule-codes
- Remote: `github.com/SjefKoopmans/haystaq-competence-proj1`, branch `main`
- **109 `BusinessRuleViolation`-codes** in `**/domain/**`
- `docs/business-rules.md` wijkt op **9 punten** af van de code

## 3. Kernrisico

100% agentic op nul fundering = een machine die onverifieerbare code produceert.
De agent heeft geen kwaliteitspoort om tegen aan te werken. **Fase 0 is niet
optioneel en niet overslaanbaar.** Zonder tests en CI is elke agent-PR een gok
die een mens volledig handmatig moet narekenen — dat is nettoverlies.

Tweede blocker: `RestExceptionHandler` gooit alle 109 rule-codes weg en
antwoordt `{"error":"invalid input"}`. Een agent kan daarmee niet vaststellen
*waarom* iets faalt. Fase 1 lost dat op.

## 4. Architectuur

```text
Jira issue -> transitie "Ready for Agent"
  -> Jira Automation -> webhook -> GitHub repository_dispatch
  -> Actions job (self-hosted runner)
  -> orchestrator agent
       ticket-refiner   (DoR-check, Gherkin AC)   [kan afbreken: needs-human]
       Explore          (read-only context)
       change-architect (implementatieplan als PR-comment)
       implementers     (domain / frontend / migration)
       test-author      (tests + testdata-MCP)
       rules-keeper     (drift-check domain/ + migration/)
       code-reviewer    (zelfreview, DDD-grenzen)
       pr-author        (conventional commit, PR body, Jira-link)
  -> CI checks
       rood  -> failure-triager, max 2 retries, dan needs-human
       groen -> pr-gatekeeper (onafhankelijke review, kent alleen ticket+diff)
                  REQUEST_CHANGES -> terug naar implementer, max 2 rondes
                  APPROVE         -> jira-scribe zet "In Review"
                                     -> MENS besluit en merget
  -> merge door mens -> jira-scribe -> Jira "Done"
  -> metrics-collector -> docs/agent-metrics/
```

**MCP-servers:**

| # | Server | Doel |
| --- | --- | --- |
| 1 | Atlassian MCP (Rovo) | Jira lezen/schrijven |
| 2 | TijdWijs Testdata MCP | Uit opdracht 1: `describe_schema`, `discover_rules`, `generate_dataset`, `load_dataset`, `verify_dataset`, `reset_environment` |
| 3 | GitHub MCP | Branches, PRs, checks, comments |
| 4 | Postgres MCP (read-only) | Schema-introspectie tijdens implementatie |

## 5. Fasering

### Fase 0 — Fundering (BLOKKEREND, ~2-3 dagen)

Doel: een kwaliteitspoort die een agent kan halen of falen.

1. Maven wrapper (`mvnw`) — reproduceerbare builds
2. `pom.xml`: surefire + failsafe, jacoco (drempel start laag, per fase
   optrekken), spotless (google-java-format AOSP), checkstyle
3. Testcontainers + RestAssured toevoegen *(parallel met 1)*
4. `backend/src/test` met **karakteriseringstests** (Feathers): *(depends on 1,3)*
   - unit: `Iban` (mod97/nl_length/format), `Hours` (step/positive),
     `IsoWeek` (firstDay/lastDay/contains/weeksInYear), `Money`,
     `EmployeeCode`, `ProjectCode`
   - domain: `Employee.register`, `Timesheet.book/submit/approve`,
     `Absence.request`, `ExpenseClaim.file`,
     `Project.start/changeStatus/assertBookableOn`
   - elke test parametrized met assertie op `ex.code()`
   - dit is tegelijk de **verificatie** van `docs/discovered-rules.md`
5. Integratielaag: `@SpringBootTest` + Testcontainers Postgres, RestAssured
   tegen de echte API, gevoed door de testdata-MCP *(depends on 4)*
6. Frontend: vitest + testing-library + eslint + prettier + `tsc --noEmit`
   *(parallel)*
7. `.editorconfig`, `CODEOWNERS`, PR-template met agent-checklist *(parallel)*
8. `.github/workflows/ci.yml`: build, spotless:check, checkstyle, unit, IT,
   jacoco-gate, frontend lint+test+build *(depends on 4,6)*
9. Branch protection op `main`: verplichte checks + verplichte review

### Fase 1 — Foutcontract & regelbron (~1 dag)

1. RFC 9457 Problem Details in `RestExceptionHandler`; rule-code als veilige,
   machine-leesbare code in de body
2. Rule-registry: parser → **gegenereerde** `docs/discovered-rules.md`;
   CI faalt bij drift *(parallel met 1)*
3. `docs/business-rules.md` corrigeren op de 9 afwijkingen *(depends on 2)*

### Fase 2 — MCP-koppelingen (~1 dag)

1. `.vscode/mcp.json` + `.mcp.json` (voor CI) met de 4 servers
2. Atlassian MCP: token in GitHub Secrets, least-privilege service-account
3. Testdata-MCP containerizen zodat de runner hem kan starten *(parallel)*
4. Postgres MCP read-only rol (alleen SELECT + `information_schema`)

### Fase 3 — Agents & skills (~2 dagen)

Bouwvolgorde: **skills eerst** (kennis), dan **agents** (gedrag), dan
**orchestrator** (regie). Zie [agent-catalogus.md](agent-catalogus.md).

### Fase 4 — Trigger & orchestratie (~1-2 dagen)

1. Jira Automation rule: transitie → webhook POST naar `repository_dispatch`
2. `.github/workflows/agent-delivery.yml`: `on: repository_dispatch`,
   concurrency-group per issue key, timeout 45 min, self-hosted runner
3. **Kill switch**: repo-variable `AGENT_ENABLED=false` stopt alles direct
4. Kostenplafond: max N runs/dag, harde token-cap per run

### Fase 5 — Observability & zelfcorrectie (~1 dag)

1. `metrics-collector`: per run JSON in `docs/agent-metrics/` (issue key, duur,
   tokens, retries, checks rood/groen, human-edits-na-review)
2. Wekelijkse rollup-workflow → `docs/agent-metrics/REPORT.md`
3. `failure-triager` met max 2 retries en verplichte escalatie
4. `retro-analyst`: leest metrics, stelt skill-verbeteringen voor als PR

### Fase 6 — Autonomiedomein uitbreiden (doorlopend)

- Ronde 1: bugfix in één bounded context, geen migratie, geen API-wijziging
- Ronde 2: + nieuwe validatieregel in `domain` + test
- Ronde 3: + nieuw API-endpoint + frontend-pagina

**Nooit autonoom:** DB-migraties met datamigratie, security, auth, breaking
API-changes, alles wat `docs/architecture.md` raakt.

## 6. Agents, skills en instructions

De volledige onderverdeling staat in [agent-catalogus.md](agent-catalogus.md):
16 agents, 11 skills, 4 instruction-bestanden en 6 slash-commands, met per
onderdeel de verantwoordelijkheid, tools, in- en output en escalatieregels.

**Twee reviewlagen, bewust gescheiden:**

| Agent | Wanneer | Context | Effect |
| --- | --- | --- | --- |
| `code-reviewer` | Vóór de PR bestaat | Kent het plan en de intentie | Interne bijsturing |
| `pr-gatekeeper` | Op de gepubliceerde PR | **Kent alleen ticket + diff** | Formele GitHub-review; blokkeert merge |

De gatekeeper krijgt bewust géén implementatieplan mee, zodat hij dezelfde
blinde blik heeft als een menselijke reviewer. Hij levert een advies met
vertrouwensniveau, een leesbudget met concrete startplek, en — het
belangrijkste — een expliciete lijst van wat hij *niet* kon verifiëren. Zo
weet de mens waar zijn vijf minuten naartoe moeten.

`pr-gatekeeper` werkt op elke PR, ook op door mensen geschreven PR's. Daarom
staat hij in de bouwvolgorde vóór de bouwketen: hij levert direct waarde
zonder dat de rest af is.

## 7. Verificatie

1. Fase 0: `mvnw verify` groen met ≥1 test per aggregate; CI faalt als een
   rule-code géén test heeft (gegenereerde registry vs testlijst)
2. Fase 1: `curl` met ongeldige IBAN → 400 met `iban.mod97` in de body
3. Fase 1: regel wijzigen in `domain/` → CI faalt op drift
4. Fase 2: agent leest via MCP een Jira-ticket en plaatst een comment
5. Fase 3: droogloop op een gescripte fixture-ticket, zonder Jira
6. Fase 3: **`pr-gatekeeper` op een bestaande, door mensen geschreven PR** —
   levert hij bevindingen die de menselijke reviewer óók had?
7. Fase 3: **negatieve test gatekeeper** — PR met opzettelijk aangepaste
   bestaande test → moet `blocker` geven en `REQUEST_CHANGES`
8. Fase 4: E2E — Jira-transitie → binnen 20 min groene PR met Jira-link,
   gatekeeper-review aanwezig, Jira staat op "In Review" met comment
9. Fase 4: **negatieve test** — ambigu ticket → agent stopt, label
   `needs-human`, géén PR
10. Fase 4: kill switch — `AGENT_ENABLED=false` stopt run binnen 1 minuut
11. Fase 5: metrics-bestand per run aanwezig; wekelijkse rollup genereert;
    gatekeeper-false-negatives worden geteld

## 8. Scope: expliciet buiten

- Auto-merge en auto-deploy
- Ticketcreatie, splitsing, story points, sprintplanning door de agent
- Autonome security-/auth-wijzigingen
- Destructieve datamigraties
- Multi-repo orkestratie
- Vervanging van de menselijke PR-review

## 9. Businesscase

**Framing 1 — doorlooptijd.** Refinement + bouw + test van een kleine wijziging
gaat van dagen naar uren. De wachttijd tussen "ticket klaar" en "iemand pakt het
op" verdwijnt; dat is doorgaans het grootste deel van de cycle time, niet het
typen. Daar komt de review-wachttijd bij: `pr-gatekeeper` doet de eerste
reviewronde onmiddellijk, waar een menselijke reviewer gemiddeld uren tot dagen
wachttijd toevoegt.

**Framing 2 — kwaliteit als bijproduct.** Fase 0 levert de repo iets wat hij nu
niet heeft: 109 businessregels onder test en een CI-poort. Die waarde blijft
staan, ook als de agentic pipeline zou falen. Dat maakt het een investering met
een risicoloos deel.

**Framing 3 — herbruikbaar capability.** De pipeline is op de skills na
repo-agnostisch. TijdWijs is de proeftuin, niet het doel.

**Meten:** DORA (lead time for change, change failure rate, deployment
frequency) plus twee agent-specifieke metrics:

1. **Percentage runs zonder menselijke correctie** — bepaalt of het
   autonomiedomein in fase 6 mag groeien
2. **Gatekeeper false negatives** — bevindingen die de mens ná de
   gatekeeper-review nog toevoegt. Blijft dat aantal hoog, dan mist de rubric
   een controle en levert de reviewagent geen echte tijdwinst

Bij C4 hoort ook: welk aandeel van de review-inspanning verschuift van mens
naar agent, en of de doorgelaten kwaliteit gelijk blijft. Dat laatste is de
enige eerlijke test van een reviewagent.

**Eerlijk over kosten:** tokenkosten per ticket, runnerkosten, en het feit dat
de reviewlast per PR níet nul wordt.

## 10. Benodigde input van de opdrachtgever

### A. Jira (blokkeert fase 2 & 4)

| # | Wat | Waarom |
| --- | --- | --- |
| A1 | Projectsleutel (bv. `TW`) | Zie `/browse/<KEY>-123` |
| A2 | Exacte statusnamen + toegestane transities (screenshot/export) | `"In Progress"` ≠ `"In progress"` |
| A3 | Besluit: nieuwe status "Ready for Agent" óf label `agent-ready` | Label is minder invasief |
| A4 | Ben je Jira-admin? | Automation rule, webhook, workflowwijziging |
| A5 | Service-account + API-token, least-privilege | Browse/Comment/Transition/Edit. Géén Delete, géén Admin |
| A6 | Mogen uitgaande webhooks naar github.com? | Netwerkbeleid |
| A7 | **Definition of Ready** | Belangrijkste input; poort van `ticket-refiner` |
| A8 | 3 goede + 3 slechte echte tickets | Regressie-fixtures voor de refiner |
| A9 | Mag de agent worklogs schrijven? | Raakt mogelijk facturatie |

### B. GitHub (blokkeert fase 0 & 4)

| # | Wat |
| --- | --- |
| B1 | Admin op de repo? (branch protection, secrets, dispatch) |
| B2 | Persoonlijke repo of organisatie? (org nodig voor CODEOWNERS-teams, environments) |
| B3 | GitHub App (voorkeur) of fine-grained PAT: `contents:write`, `pull_requests:write`, `actions:read`, `checks:read` |
| B4 | Welk model/licentie mag de agent in CI gebruiken? |
| B5 | Runner: self-hosted (waar?) of GitHub-hosted? Self-hosted vereist Docker + Java 21 + Node en Postgres voor Testcontainers |
| B6 | Wie in CODEOWNERS / verplichte reviewer van agent-PRs? |

### C. Beleid & governance (blokkeert fase 3 & 6)

| # | Wat |
| --- | --- |
| C1 | Tokenbudget per maand + wie krijgt melding bij 80%? |
| C2 | Ticketklasse voor ronde 1 (voorstel: bugfix, één context, geen migratie) |
| C3 | Eigenaar/aanspreekpunt als de agent iets stuk maakt |
| C4 | Verplicht label `agent-generated`? (advies: altijd) |
| C5 | AVG/security-akkoord: ticketinhoud + code naar een LLM-endpoint |
| C6 | Acceptatieomgeving of alleen lokaal `docker compose`? |
| C7 | Telt de `pr-gatekeeper`-review mee als verplichte approval in branch protection, of blijft alleen de menselijke review bindend? *(advies: alleen mens bindend; gatekeeper blokkeert wel bij `REQUEST_CHANGES`)* |
| C8 | Drempels voor de gatekeeper: bij hoeveel gewijzigde bestanden/regels moet hij "splits deze PR" adviseren? |

### D. Vakinhoudelijk (blokkeert fase 0 & 1)

| # | Wat |
| --- | --- |
| D1 | De 9 doc/code-afwijkingen: welke is bug, welke doc-fout? Concreet: bonnummer verplicht boven 25,00 of 50,00 euro? |
| D2 | Bestaat TW-871 op deze site en mag ik het afsluiten met de gevonden oorzaak? |
| D3 | Mag `RestExceptionHandler` naar RFC 9457? **Blocker voor de pipeline** |
| D4 | Functioneel eigenaar voor regelvragen (opvolger R. Mulder) |
| D5 | Parseert de frontend nu foutmeldingen die RFC 9457 zou breken? |

### E. Praktisch

| # | Wat |
| --- | --- |
| E1 | Toegang tot de Jira-site, óf screenshots van workflow + 6 tickets |
| E2 | Confluence-documentatie die de agent moet lezen? |
| E3 | Timebox: uren (competence-dag) of weken (echt project)? |

**Minimale set om te starten:** A1, A2, A3, A5, B1, B3, B5, D3, E3.

## 11. Anti-patronen (expliciet vermijden)

1. Agentic bouwen bovenop nul tests → onverifieerbare code
2. Eén grote alles-kunnende agent → onmogelijk te debuggen; kies kleine agents
   met scherpe verantwoordelijkheid
3. Geen escalatiepad → de agent gaat door waar hij had moeten stoppen
4. Onbeperkte retries → tokenkosten exploderen, PR wordt onreviewbaar
5. Geen metrics → geen verhaal naar management na de pilot
6. Autonomiedomein te breed beginnen → vertrouwen verspeeld bij de eerste
   slechte PR
