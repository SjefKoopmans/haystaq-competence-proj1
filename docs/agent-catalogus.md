# Agent-catalogus: complete onderverdeling

> Hoort bij [agentic-workflow-plan.md](agentic-workflow-plan.md).
> Dit bestand beschrijft **wie wat doet** in de Jira-naar-PR pipeline:
> 16 agents, 11 skills, 4 instruction-bestanden, 6 slash-commands.

## Ontwerpprincipes

1. **Eén agent, één verantwoordelijkheid.** Een grote alles-kunnende agent is
   niet te debuggen en niet te verbeteren. Kleine agents met een scherpe taak
   zijn afzonderlijk testbaar.
2. **Skills bevatten kennis, agents bevatten gedrag.** Een skill is een
   naslagwerk (hoe zit deze codebase in elkaar). Een agent is een rol met
   tools en beslisregels. Kennis wordt hergebruikt door meerdere agents.
3. **Least privilege per agent.** `code-reviewer` krijgt geen edit-tools.
   `jira-scribe` krijgt geen filesystem. Dat beperkt de schade bij een
   verkeerde beslissing.
4. **Elke agent kan escaleren.** Niet doorgaan bij twijfel is een feature,
   geen falen. Het label `needs-human` is het gewenste eindresultaat bij een
   onduidelijk ticket.
5. **Contract in, contract uit.** Iedere agent krijgt gedefinieerde input en
   levert gedefinieerde output. De orchestrator bewaart de state in een
   PR-comment, zodat een herstart niet opnieuw begint.

## Bestandsstructuur

```text
.github/
├── copilot-instructions.md              repo-brede basis (altijd geladen)
├── agents/
│   ├── orchestrator.agent.md
│   ├── ticket-refiner.agent.md
│   ├── change-architect.agent.md
│   ├── domain-implementer.agent.md
│   ├── frontend-implementer.agent.md
│   ├── migration-author.agent.md
│   ├── test-author.agent.md
│   ├── rules-keeper.agent.md
│   ├── code-reviewer.agent.md
│   ├── pr-author.agent.md
│   ├── pr-gatekeeper.agent.md
│   ├── failure-triager.agent.md
│   ├── jira-scribe.agent.md
│   ├── metrics-collector.agent.md
│   └── retro-analyst.agent.md
├── skills/
│   ├── tijdwijs-architecture/SKILL.md
│   ├── tijdwijs-domain-rules/SKILL.md
│   ├── tijdwijs-testdata/SKILL.md
│   ├── test-strategy/SKILL.md
│   ├── error-contract/SKILL.md
│   ├── flyway-migrations/SKILL.md
│   ├── jira-conventions/SKILL.md
│   ├── pr-conventions/SKILL.md
│   ├── pr-review-rubric/SKILL.md
│   ├── agent-escalation/SKILL.md
│   └── frontend-conventions/SKILL.md
├── instructions/
│   ├── java-backend.instructions.md
│   ├── sql-migrations.instructions.md
│   ├── frontend.instructions.md
│   └── docs.instructions.md
├── prompts/
│   ├── ticket.prompt.md
│   ├── refine.prompt.md
│   ├── testdata.prompt.md
│   ├── rules-drift.prompt.md
│   ├── review-pr.prompt.md
│   └── agent-report.prompt.md
└── workflows/
    ├── ci.yml
    ├── agent-delivery.yml
    └── agent-report.yml
```

---

## Deel 1 — De 15 agents

### Overzicht per fase in de keten

| # | Fase | Agent | Mag afbreken? |
| --- | --- | --- | --- |
| 1 | Regie | `orchestrator` | ja |
| 2 | Intake | `ticket-refiner` | **ja — harde poort** |
| 3 | Context | `Explore` *(built-in)* | nee |
| 4 | Ontwerp | `change-architect` | ja |
| 5 | Bouw | `domain-implementer` | nee |
| 6 | Bouw | `frontend-implementer` | nee |
| 7 | Bouw | `migration-author` | **ja — destructieve DDL** |
| 8 | Test | `test-author` | nee |
| 9 | Bewaking | `rules-keeper` | **ja — ongedocumenteerde regelwijziging** |
| 10 | Zelfreview | `code-reviewer` | ja |
| 11 | Oplevering | `pr-author` | nee |
| 12 | **PR-review** | **`pr-gatekeeper`** | **ja — blokkeert de PR** |
| 13 | Herstel | `failure-triager` | **ja — na 2 retries** |
| 14 | Communicatie | `jira-scribe` | nee |
| 15 | Meting | `metrics-collector` | nee |
| 16 | Verbetering | `retro-analyst` | nee |

### Twee reviewers, bewust gescheiden

| | `code-reviewer` (#10) | `pr-gatekeeper` (#12) |
| --- | --- | --- |
| Wanneer | Vóór de PR bestaat | Op de gepubliceerde PR |
| Context | Kent het plan en de intentie | **Kent alleen ticket + diff** |
| Vraag | "Klopt dit met wat we van plan waren?" | "Zou ik dit als collega goedkeuren?" |
| Output | Bevindingen terug naar implementer | Formele GitHub-review met inline comments |
| Effect | Interne bijsturing | **Blokkeert merge bij `REQUEST_CHANGES`** |
| Doel | Fouten vroeg wegnemen | Jou een reviewbare PR geven, niet een ruwe diff |

De scheiding is opzettelijk. `code-reviewer` weet wat de bedoeling was en is
daardoor bevooroordeeld: hij ziet wat hij verwacht te zien. `pr-gatekeeper`
krijgt bewust géén implementatieplan mee — alleen het ticket en de diff, net
als jij. Dat is de enige manier om te vinden wat de bouwketen structureel
mist.

---

### 1. `orchestrator` — Regie

| | |
| --- | --- |
| **Doel** | De keten van begin tot eind aansturen en de state bewaken |
| **Input** | Jira issue key uit de `repository_dispatch` payload |
| **Output** | Een groene PR met Jira-link, óf een `needs-human` escalatie |
| **Tools** | Atlassian MCP, GitHub MCP, `runSubagent` |
| **Skills** | `jira-conventions`, `agent-escalation` |
| **Escaleert bij** | Elke subagent die afbreekt; timeout > 45 min; >N gewijzigde bestanden |

**Beslisregels:**

- State wordt bijgehouden in één PR-comment met een checklist per fase. Bij
  herstart wordt die comment gelezen — geen dubbel werk.
- Stopt onmiddellijk als repo-variable `AGENT_ENABLED=false`.
- Geeft nooit zelf code-edits; delegeert altijd.
- Één run per issue key (concurrency-group), zodat twee agents niet dezelfde
  branch schrijven.

---

### 2. `ticket-refiner` — Intake en Definition of Ready

| | |
| --- | --- |
| **Doel** | Vaststellen of een ticket *überhaupt* geschikt is voor de agent |
| **Input** | Jira issue (samenvatting, beschrijving, labels, comments, links) |
| **Output** | Gherkin acceptatiecriteria + verrijkt ticket, óf `needs-human` |
| **Tools** | Atlassian MCP (lezen + comment), read-only filesystem |
| **Skills** | `jira-conventions`, `tijdwijs-architecture`, `agent-escalation` |
| **Escaleert bij** | Ontbrekende DoR-velden, ambiguïteit, tegenstrijdige AC, meerdere bounded contexts |

**Dit is de belangrijkste agent van de hele keten.** Alles wat hier doorglipt,
kost verderop tienvoudig. Een ticket dat wordt afgewezen met een concrete vraag
in Jira is een *succes*, geen mislukking.

**Checklist die hij afvinkt:**

- Is het probleem beschreven in termen van gedrag, niet van oplossing?
- Zijn er acceptatiecriteria, en zijn die testbaar?
- Welke bounded context(en) raakt dit? Meer dan één → escaleren.
- Is er een DB-migratie nodig? Zo ja → labelt `needs-migration-review`.
- Raakt dit een van de 109 bestaande businessregels? Zo ja → welke code?
- Bij een bug: staan er reproductiestappen in?

---

### 3. `Explore` *(built-in)* — Contextverzameling

| | |
| --- | --- |
| **Doel** | Read-only de relevante code vinden zonder de hoofdcontext te vervuilen |
| **Input** | Vraag van de orchestrator of change-architect |
| **Output** | Feitelijk rapport met bestandspaden |
| **Tools** | read/search (read-only) |

Bestaande VS Code subagent — niet zelf bouwen. Wordt parallel aangeroepen voor
meerdere vragen tegelijk.

---

### 4. `change-architect` — Ontwerp

| | |
| --- | --- |
| **Doel** | Bepalen *waar* de wijziging hoort volgens de DDD-indeling |
| **Input** | Verrijkt ticket + Explore-rapport |
| **Output** | Implementatieplan als PR-comment: bestanden, lagen, volgorde |
| **Tools** | read-only filesystem, Postgres MCP (read-only) |
| **Skills** | `tijdwijs-architecture`, `tijdwijs-domain-rules`, `error-contract` |
| **Escaleert bij** | Wijziging die een bounded context-grens overschrijdt, nieuwe poort/adapter nodig, wijziging in `docs/architecture.md` |

**Kernvraag die hij beantwoordt:** hoort deze regel in het **aggregate** (kan
het zelf zien) of in de **applicatielaag** (heeft kennis van een ander
aggregate nodig)? Dat is de meestgemaakte fout in deze codebase-vorm.

---

### 5. `domain-implementer` — Backend-implementatie

| | |
| --- | --- |
| **Doel** | Code schrijven in `domain`/`application`/`api`/`infrastructure` |
| **Input** | Implementatieplan |
| **Output** | Gewijzigde Java-bestanden |
| **Tools** | edit, read, terminal (`mvnw`) |
| **Skills** | `tijdwijs-architecture`, `tijdwijs-domain-rules`, `error-contract` |
| **Instructions** | `java-backend.instructions.md` (auto via `applyTo`) |

**Harde regels:**

- Geen Spring-imports in `domain/`
- Geen setters op aggregates; wijzigen via gedrag dat invarianten herbewaakt
- Nieuwe validatie → `BusinessRuleViolation.require`/`requireState` met code in
  het formaat `<veld>.<regel>`
- Value objects zijn `record`s met validatie in de compacte constructor
- Constructor-injectie, geen `@Autowired` op velden

---

### 6. `frontend-implementer` — Frontend-implementatie

| | |
| --- | --- |
| **Doel** | React/TypeScript-wijzigingen |
| **Input** | Implementatieplan |
| **Output** | Gewijzigde `.ts`/`.tsx`-bestanden |
| **Tools** | edit, read, terminal (`npm`) |
| **Skills** | `frontend-conventions`, `error-contract` |
| **Instructions** | `frontend.instructions.md` |

Hergebruikt de bestaande `DataTable` en `EntityForm` componenten; alle HTTP
loopt via `api.ts`; types in `types.ts`. Geen `any`.

---

### 7. `migration-author` — Databasemigraties

| | |
| --- | --- |
| **Doel** | Flyway-migraties schrijven |
| **Input** | Implementatieplan met schemawijziging |
| **Output** | Nieuw bestand `V{n}__<naam>.sql` |
| **Tools** | edit, read, Postgres MCP (read-only) |
| **Skills** | `flyway-migrations` |
| **Instructions** | `sql-migrations.instructions.md` |
| **Escaleert bij** | `DROP`, `ALTER ... TYPE` met dataverlies, `NOT NULL` op een gevulde kolom zonder default |

**Harde regels:**

- Forward-only. `V1__schema.sql` en `V2__seed.sql` worden **nooit** gewijzigd
- `ddl-auto: validate` betekent: entiteit en schema moeten exact synchroon,
  anders start de applicatie niet
- Check-constraints krijgen een naam, zodat een falende test bruikbaar is

---

### 8. `test-author` — Tests

| | |
| --- | --- |
| **Doel** | Tests schrijven die de wijziging bewijzen *en* de regel vastleggen |
| **Input** | Gewijzigde code + Gherkin AC uit het ticket |
| **Output** | Unit- en integratietests |
| **Tools** | edit, read, terminal, **Testdata MCP** |
| **Skills** | `test-strategy`, `tijdwijs-testdata` |

**Verplichtingen:**

- Elke nieuwe businessregel krijgt een test die op de **rule-code** asserteert,
  niet op de HTTP-status. Een test die `409` verwacht bewijst niets: er zijn
  tientallen redenen voor een 409.
- Boundary Value Analysis: bij elk numeriek veld min−1, min, max, max+1
- Naamgeving: `should_<gedrag>_when_<conditie>`
- Integratietests gebruiken de Testdata-MCP voor een geldige entiteitsketen
  (member → project → periode → ISO-week), niet handgeschreven SQL

---

### 9. `rules-keeper` — Drift-bewaking

| | |
| --- | --- |
| **Doel** | Voorkomen dat de businessregels en de documentatie uiteenlopen |
| **Input** | Diff van de PR |
| **Output** | Bijgewerkte `docs/discovered-rules.md` + verschilrapport |
| **Tools** | read, edit, terminal |
| **Skills** | `tijdwijs-domain-rules` |
| **Escaleert bij** | Een regel die is gewijzigd of verwijderd zonder dat het ticket dat vroeg |

Draait bij elke wijziging in `**/domain/**` of `**/db/migration/**`. Dit is de
agent die het probleem uit de nulmeting structureel oplost: documentatie kan
niet meer verouderen omdat ze wordt gegenereerd, en CI faalt bij drift.

---

### 10. `code-reviewer` — Zelfreview

| | |
| --- | --- |
| **Doel** | De PR beoordelen vóórdat een mens hem ziet |
| **Input** | Volledige diff |
| **Output** | Reviewrapport; bij bevindingen terug naar de implementer |
| **Tools** | **read-only** (bewust geen edit) |
| **Skills** | `tijdwijs-architecture`, `test-strategy`, `error-contract`, `pr-conventions` |

**Controleert:**

- Bounded context-lekken (import uit een andere context, repository van een
  ander context)
- Spring-import in `domain/`
- Regel in het verkeerde laag (aggregate vs applicatielaag)
- Ontbrekende test op een nieuwe rule-code
- N+1 queries, `FetchType.EAGER` op nieuwe collecties
- Secrets, hardcoded URLs, hardcoded schema

Read-only is opzettelijk: een reviewer die zijn eigen bevindingen mag
oplossen, vindt minder.

---

### 11. `pr-author` — Oplevering

| | |
| --- | --- |
| **Doel** | Een reviewbare PR met een eerlijke samenvatting |
| **Input** | Commits + reviewrapport |
| **Output** | PR met titel, body, labels, Jira-link |
| **Tools** | GitHub MCP |
| **Skills** | `pr-conventions`, `jira-conventions` |

**PR-body bevat verplicht:**

- Link naar het Jira-ticket
- Wat er is gewijzigd en waarom (in termen van het ticket)
- **Review-focus:** waar de reviewer specifiek naar moet kijken
- Disclosure: `agent-generated`, met welke agents eraan hebben gewerkt
- Wat er *niet* is gedaan en waarom

Conventional Commits, zodat changelog-generatie later mogelijk blijft.

---

### 12. `pr-gatekeeper` — Onafhankelijke PR-review

| | |
| --- | --- |
| **Doel** | De PR beoordelen zoals een kritische collega dat zou doen, zodat de mens alleen nog *besluit* in plaats van *uitzoekt* |
| **Input** | Jira-ticket + PR-diff + CI-resultaten. **Expliciet niet:** het implementatieplan, de ketenlogs, de state-comment |
| **Output** | Formele GitHub-review: `APPROVE`, `COMMENT` of `REQUEST_CHANGES`, met inline comments en een samenvatting bovenaan |
| **Tools** | GitHub MCP (review plaatsen), read-only filesystem, terminal (read-only: tests draaien, niet wijzigen) |
| **Skills** | `pr-review-rubric`, `tijdwijs-architecture`, `test-strategy`, `error-contract`, `tijdwijs-domain-rules` |
| **Escaleert bij** | `REQUEST_CHANGES` → terug naar de implementer (max 2 rondes), daarna `needs-human` |

**Waarom een aparte agent en niet gewoon `code-reviewer` uitbreiden:**
onafhankelijkheid is de hele waarde. Een reviewer die het plan heeft gelezen,
controleert of de code het plan volgt. Een reviewer die alleen het ticket en de
diff ziet, controleert of de code *het probleem oplost*. Dat tweede is wat jij
doet, en dat is wat je wilt automatiseren.

#### Wat hij oplevert bovenaan de PR

Een vaste samenvatting, zodat je in 30 seconden weet waar je aan toe bent:

```markdown
## Review door pr-gatekeeper

**Advies:** APPROVE / REQUEST_CHANGES
**Vertrouwen:** hoog / midden / laag
**Leesbudget voor de mens:** ~N minuten, focus op <bestand:regel>

### Lost dit het ticket op?
<expliciet per acceptatiecriterium: gedekt / niet gedekt / deels>

### Bevindingen
| Ernst | Waar | Wat | Waarom |
|---|---|---|---|
| blocker | ... | ... | ... |
| belangrijk | ... | ... | ... |
| suggestie | ... | ... | ... |

### Wat ik NIET heb kunnen verifiëren
<expliciete lijst — dit is waar de mens moet kijken>

### Wat er buiten het ticket om is gewijzigd
<scope creep, ongevraagde refactors>
```

De laatste twee secties zijn de belangrijkste. Een reviewagent die alleen
bevindingen geeft, wekt valse zekerheid. Door expliciet te benoemen wat hij
*niet* kon controleren, weet je precies waar jouw 5 minuten naartoe moeten.

#### Ernstclassificatie (bindend)

| Ernst | Betekenis | Gevolg |
| --- | --- | --- |
| `blocker` | Fout gedrag, ontbrekende test op een businessregel, contextlek, security, dataverlies | `REQUEST_CHANGES`, terug naar implementer |
| `belangrijk` | Werkt wel, maar schendt een conventie of maakt onderhoud duurder | `REQUEST_CHANGES` bij ≥3, anders comment |
| `suggestie` | Smaak, optimalisatie, toekomstig werk | Comment; blokkeert nooit |

#### Verplichte controles

**Doet het wat het ticket vraagt?**

- Elk acceptatiecriterium expliciet afvinken tegen de diff
- Ontbreekt er een AC → `blocker`
- Zit er functionaliteit in die het ticket níet vroeg → `belangrijk` (scope creep)

**Zijn de tests echte tests?**

- Nieuwe businessregel zonder test die op de **rule-code** asserteert → `blocker`
- Test die alleen `409` verwacht → `blocker`; er zijn tientallen redenen voor
  een 409 in deze codebase, zo'n test bewijst niets
- Bestaande test aangepast → altijd `blocker` tenzij de PR-body onderbouwt
  waarom de oude verwachting fout was. **Dit is de belangrijkste controle van
  de hele agent**: een agent die een test aanpast om hem groen te krijgen laat
  een regressie stil verdwijnen
- Alleen happy path → `belangrijk`; waar zijn de grenswaarden?
- Assertie op een implementatiedetail in plaats van gedrag → `belangrijk`

**Respecteert het de architectuur?**

- Import uit een ander bounded context → `blocker`
- Repository van een ander context gebruikt in plaats van een poort → `blocker`
- Spring-import in `domain/` → `blocker`
- Regel in de verkeerde laag (aggregate vs applicatielaag) → `belangrijk`
- Nieuwe `FetchType.EAGER` op een collectie → `belangrijk`
- Setter toegevoegd op een aggregate → `blocker`

**Is het veilig?**

- Secrets, tokens, hardcoded URLs of connectiestrings → `blocker`
- Rule-code geëxposeerd die iets over interne staat verklapt → `belangrijk`
- Migratie met `DROP`, `ALTER TYPE` of `NOT NULL` op gevulde kolom → `blocker`
- Bestaande migratie gewijzigd in plaats van een nieuwe → `blocker`

**Is het reviewbaar?**

- Diff > N bestanden of > N regels → `belangrijk` met voorstel tot splitsen
- Ongerelateerde formatting die de echte wijziging verbergt → `belangrijk`
- PR-body ontbreekt review-focus of agent-disclosure → `belangrijk`

#### Wat hij absoluut niet mag

| Verbod | Reden |
| --- | --- |
| Code wijzigen | Reviewer en auteur scheiden. Read-only, hard |
| Zichzelf approven na een fix-ronde zonder de diff opnieuw te lezen | Anders is de poort een formaliteit |
| `APPROVE` bij rode CI | Ongeacht hoe goed de code eruitziet |
| `APPROVE` als er een `blocker` open staat | Geen uitzonderingen |
| Bevindingen weglaten omdat de PR "verder goed is" | Volledigheid boven vriendelijkheid |
| De menselijke review vervangen | Hij is de op-één-na-laatste stap, niet de laatste |

#### Interactie met de mens

Na `APPROVE` door de gatekeeper zet `jira-scribe` het ticket op "In Review" en
komt de PR bij jou. Je krijgt dan:

1. Een advies met vertrouwensniveau
2. Een leesbudget met een concrete plek om te beginnen
3. Een expliciete lijst van wat níet geverifieerd is

Bij `REQUEST_CHANGES` gaat de PR terug in de loop en zie jij hem pas na
maximaal 2 herstelrondes. Daarna escaleert de keten naar `needs-human` — een
PR die twee keer is afgekeurd door de eigen poort is een signaal dat het ticket
of de aanpak niet klopt, niet dat er nog een ronde nodig is.

---

### 13. `failure-triager` — Herstel bij rode CI

| | |
| --- | --- |
| **Doel** | Falende checks diagnosticeren en herstellen, of eerlijk opgeven |
| **Input** | Falende check-run + logs |
| **Output** | Fix-commit, óf escalatie met diagnose |
| **Tools** | GitHub MCP (logs), read, edit, terminal |
| **Skills** | `agent-escalation`, `error-contract`, `test-strategy` |
| **Escaleert bij** | **Na maximaal 2 pogingen — hard plafond** |

**Werkwijze:** log lezen → hypothese formuleren → minimale fix → hertesten.
Nooit een test aanpassen om hem groen te krijgen zonder expliciete
onderbouwing waarom de test fout was; dat is de gevaarlijkste faalmodus van
een agent en wordt in de skill expliciet verboden.

---

### 14. `jira-scribe` — Communicatie terug naar de business

| | |
| --- | --- |
| **Doel** | Jira actueel houden zonder menselijke tussenkomst |
| **Input** | Ketengebeurtenissen van de orchestrator |
| **Output** | Statusovergangen, comments, PR-link, eventueel worklog |
| **Tools** | **Alleen** Atlassian MCP (geen filesystem) |
| **Skills** | `jira-conventions` |

**Schrijft:**

- Bij start: status → "In Progress" + comment "agent gestart, run-link"
- Bij PR: status → "In Review" + comment met PR-link
- Bij escalatie: label `needs-human` + comment met **de concrete vraag**
- Bij merge: status → "Done"

**Schrijft nooit:** nieuwe tickets, story points, sprinttoewijzing,
prioriteitswijzigingen. Dat blijft mensenwerk (buiten scope, zie plan §8).

---

### 15. `metrics-collector` — Meting

| | |
| --- | --- |
| **Doel** | Elke run meetbaar maken |
| **Input** | Run-metadata |
| **Output** | JSON in `docs/agent-metrics/<datum>-<issue-key>.json` |
| **Tools** | edit, GitHub MCP |

**Velden:** issue key, ticketklasse, doorlooptijd per fase, tokenverbruik,
aantal retries, checks rood/groen bij eerste poging, aantal bestanden,
escalatie ja/nee met reden, en na merge: aantal menselijke correctie-commits.

Die laatste is de belangrijkste metric van het hele project: **percentage runs
zonder menselijke correctie** bepaalt of het autonomiedomein mag groeien.

**Extra velden voor `pr-gatekeeper`** — deze bepalen of de reviewagent zijn
werk doet:

| Metric | Wat het zegt |
| --- | --- |
| Bevindingen per ernst | Volume en zwaarte van wat hij vindt |
| Advies (`APPROVE`/`REQUEST_CHANGES`) | Strengheid |
| Aantal herstelrondes | Efficiëntie van de loop |
| **Bevindingen die de mens erna nog toevoegt** | **False negatives — wat hij mist** |
| **Bevindingen die de mens verwerpt** | **False positives — waar hij te streng is** |

Die laatste twee zijn cruciaal. Blijft de mens dezelfde soort bevindingen
toevoegen? Dan mist de rubric een controle en moet `retro-analyst` die
toevoegen. Verwerpt de mens structureel dezelfde bevindingen? Dan is de
gatekeeper te streng en kost hij tijd in plaats van dat hij die spaart. Zonder
deze twee metrics weet je niet of de reviewagent netto waarde levert.

---

### 16. `retro-analyst` — Zelfverbetering

| | |
| --- | --- |
| **Doel** | De pipeline laten leren van zijn eigen fouten |
| **Input** | Alle metrics-bestanden van de afgelopen week |
| **Output** | `docs/agent-metrics/REPORT.md` + PR met skill-verbeteringen |
| **Tools** | read, edit, GitHub MCP |

Draait wekelijks op een schedule. Zoekt patronen: welke escalatiereden komt
het meest voor, welke check faalt structureel bij de eerste poging, welke
skill mist informatie. Stelt concrete tekstwijzigingen aan de skills voor —
als PR, dus met menselijke review.

Dit is wat de pipeline onderscheidt van een script: de feedbackloop uit
opdracht 1, toegepast op de agents zelf.

---

## Deel 2 — De 10 skills

Skills zijn naslagwerken. Ze bevatten geen gedrag, alleen kennis die meerdere
agents nodig hebben.

| Skill | Inhoud | Gebruikt door |
| --- | --- | --- |
| `tijdwijs-architecture` | Bounded contexts, laagverantwoordelijkheden, poort/adapter-patroon, aggregate-overzicht, wat NOOIT mag (context-crossing import, repository van een ander context, Spring in domain) | architect, implementers, reviewer |
| `tijdwijs-domain-rules` | Hoe een regel toevoegen: `require` (400) vs `requireState` (409), code-naamgeving `<veld>.<regel>`, beslisboom aggregate vs applicatielaag, de bestaande 109 codes gegroepeerd | domain-implementer, rules-keeper |
| `tijdwijs-testdata` | De MCP uit opdracht 1: beschikbare profielen, seed-gedrag, reset-protocol, hoe je een geldige weekstaat-keten opbouwt (member → project → dienstverband → ISO-week) | test-author |
| `test-strategy` | Testpiramide, Boundary Value Analysis, equivalentieklassen, Test Data Builder-patroon, naamgevingsconventie, **verplichte assertie op rule-code**, verbod op tests groenmaken zonder onderbouwing | test-author, reviewer, failure-triager |
| `error-contract` | RFC 9457 Problem Details, onderscheid veilige vs interne codes, hoe een nieuwe rule-code exposen, waarom `{"error":"invalid input"}` een blocker was | implementers, reviewer, failure-triager |
| `flyway-migrations` | Forward-only, naamgeving, verbod op wijzigen van bestaande migraties, `ddl-auto: validate`-implicaties, reset-protocol, destructieve DDL-lijst | migration-author |
| `jira-conventions` | Exacte statusnamen, DoR-checklist, DoD, labelconventies, smart commits, wat een agent wel/niet in Jira mag schrijven | refiner, scribe, orchestrator |
| `pr-conventions` | Conventional Commits, PR-body-template, verplichte agent-disclosure, review-focus-sectie | pr-author |
| `pr-review-rubric` | De volledige reviewchecklist met ernstclassificatie, het reviewsamenvatting-template, de verboden voor een reviewagent, en hoe je "wat ik niet kon verifiëren" formuleert | **pr-gatekeeper**, code-reviewer |
| `agent-escalation` | Wanneer stoppen: ambigu ticket, security/auth, migratie met dataverlies, >N bestanden, architectuurwijziging, 2 mislukte retries. Hoe escaleren: label + concrete vraag in Jira | **alle agents** |
| `frontend-conventions` | React 18 + Vite-patronen in deze repo: `api.ts` als enige HTTP-laag, `types.ts`, hergebruik van `DataTable`/`EntityForm`, strict TS | frontend-implementer |

---

## Deel 3 — Instructions (automatisch geladen)

Instructions worden via `applyTo` automatisch toegevoegd wanneer een agent een
bestand in dat patroon aanraakt. Geen agent hoeft ze expliciet te lezen.

| Bestand | applyTo | Kerninhoud |
| --- | --- | --- |
| `java-backend.instructions.md` | `backend/**/*.java` | Java 21, geen Lombok, constructor-injectie, geen setters op aggregates, `record` voor value objects, geen Spring in `domain/` |
| `sql-migrations.instructions.md` | `backend/**/db/migration/*.sql` | Forward-only, lowercase, benoemde check-constraints, nooit bestaande migratie wijzigen |
| `frontend.instructions.md` | `frontend/src/**/*.{ts,tsx}` | Strict TS, geen `any`, fetch uitsluitend via `api.ts` |
| `docs.instructions.md` | `docs/**/*.md` | Nederlands; `discovered-rules.md` is gegenereerd — niet handmatig wijzigen |

Plus `.github/copilot-instructions.md` als repo-brede basis: stack, poorten,
reset-commando, en de instructie "lees eerst de `tijdwijs-architecture` skill".

---

## Deel 4 — Slash-commands

Voor handmatig gebruik en debugging — de pipeline draait normaal onbemand.

| Command | Doel |
| --- | --- |
| `/ticket <KEY>` | Eén ticket handmatig door de keten duwen, lokaal, met volledige uitvoer |
| `/refine <KEY>` | Alleen refinement uitvoeren, output naar Jira. Bruikbaar voor de PO zonder de rest van de keten |
| `/testdata <profiel>` | De toolkit uit opdracht 1 als voordeur |
| `/rules-drift` | Drift-check handmatig uitvoeren |
| `/review-pr <nummer>` | `pr-gatekeeper` los aanroepen op een willekeurige PR — **ook op PR's die een mens heeft geschreven** |
| `/agent-report` | Metrics-rollup op verzoek |

`/review-pr` is de goedkoopste winst uit dit hele plan. De gatekeeper werkt op
elke PR, ongeacht wie hem schreef. Je kunt hem vandaag inzetten op menselijke
PR's zonder dat de rest van de pipeline bestaat.

---

## Deel 5 — Bouwvolgorde

Skills eerst, agents daarna, orchestrator laatst. Een agent zonder skill
improviseert; een orchestrator zonder agents heeft niets te delegeren.

| Stap | Wat | Waarom deze volgorde |
| --- | --- | --- |
| 1 | `tijdwijs-architecture`, `tijdwijs-domain-rules`, `test-strategy`, `agent-escalation` | De vier skills die bijna elke agent nodig heeft |
| 2 | `error-contract`, `flyway-migrations`, `frontend-conventions` | Domeinspecifieke aanvulling |
| 3 | `jira-conventions`, `pr-conventions` | Vereisen input A2/A7 van de opdrachtgever |
| 4 | Instructions + `copilot-instructions.md` | Werken direct, ook zonder agents |
| 5 | **`pr-review-rubric` + `pr-gatekeeper` + `/review-pr`** | **Naar voren gehaald: werkt op menselijke PR's, dus direct waarde zonder de rest van de keten** |
| 6 | `ticket-refiner` | De intake-poort. Los testbaar met de 6 fixture-tickets (A8) |
| 7 | `change-architect`, `domain-implementer`, `test-author` | De kern van de bouwketen |
| 8 | `code-reviewer`, `rules-keeper` | Interne kwaliteitspoorten |
| 9 | `pr-author`, `jira-scribe` | Oplevering en communicatie |
| 10 | `failure-triager` | Vereist werkende CI (fase 0) |
| 11 | `frontend-implementer`, `migration-author` | Nodig vanaf autonomieronde 2-3 |
| 12 | `orchestrator` | Kan pas als de onderdelen los werken |
| 13 | `metrics-collector`, `retro-analyst` | Zonder deze twee heb je na de pilot geen verhaal |

**Let op stap 5.** `pr-gatekeeper` staat bewust vóór de hele bouwketen. Hij is
de enige agent die zelfstandig waarde levert zonder dat er iets anders af is:
je richt hem vandaag op de PR's die je team met de hand schrijft. Dat levert
meteen twee dingen op — betere reviews, en een geijkte rubric die later de
poort van de agentic keten wordt.

---

## Deel 6 — Wat opzettelijk géén agent is

| Taak | Waarom niet |
| --- | --- |
| Merge naar `main` | Menselijk checkpoint, bewust besluit |
| Deploy | Buiten scope |
| Ticketcreatie, splitsen, story points, sprintplanning | Businessverantwoordelijkheid; een agent die zijn eigen werkvoorraad maakt is niet te controleren |
| Security- en auth-wijzigingen | Te hoog risico voor autonome uitvoering |
| Destructieve datamigraties | Onomkeerbaar |
| Architectuurbesluiten | Raakt `docs/architecture.md`; menselijk |
| Prioriteren tussen tickets | Businessverantwoordelijkheid |
