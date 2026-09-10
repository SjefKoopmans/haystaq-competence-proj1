# Testen — TijdWijs backend

> Status: fase 0 afgerond voor Persoon A (blok 1-4). jacoco/spotless/checkstyle
> (blok 5) zijn **bewust overgeslagen** — zie "Bewust weggelaten" onderaan.
> Zie [agentic-workflow-plan.md](agentic-workflow-plan.md) §5 voor de fasering.

Dit document beschrijft hoe de kwaliteitspoort werkt en welk patroon
testklassen volgen. Het is de overdracht aan Persoon B (integratietests,
`discovered-rules.md`) en Persoon C (`ci.yml`).

## Bouwen en draaien

Er is een Maven wrapper; Maven hoeft niet geïnstalleerd te zijn. Wel een
JDK 21 met `JAVA_HOME` gezet.

```powershell
cd backend
.\mvnw.cmd test                    # unit-tests (*Test), snel, geen database
.\mvnw.cmd verify                  # unit + integratietests (*IT)
.\mvnw.cmd test -Dtest='IbanTest'  # één klasse
```

| Onderdeel | Keuze | Reden |
| --- | --- | --- |
| Maven wrapper | 3.9.9, `distributionType=only-script` | reproduceerbare build op elke machine en op de CI-runner |
| JDK | Temurin 21 | komt overeen met `java.version` in `pom.xml` |
| Unit-tests | `**/*Test.java`, surefire, aan `test` | seconden; bruikbaar tijdens ontwikkelen |
| Integratietests | `**/*IT.java`, failsafe, aan `verify` | traag (Testcontainers); mag de snelle lus niet blokkeren |
| `failIfNoSpecifiedTests` | `true` | een groene build met 0 tests is de klassieke valkuil |

**Niet toegevoegd:** AssertJ 3.26.3, JUnit 5.11.4 en `junit-jupiter-params`
zitten al in `spring-boot-starter-test`. Extra declaraties zouden alleen
versieconflicten opleveren.

## Het testpatroon

`IbanTest` is de referentie. Nieuwe testklassen volgen deze vorm:

1. Eén `@Nested` klasse per rule-code, plus één voor geldige invoer en één
   voor overig gedrag.
2. Asserteren via `Violations.assertInvalid` / `assertConflict` /
   `assertNotFound` — die controleren `code()` **én** `kind()`.
3. `@ParameterizedTest` met `@CsvSource` / `@ValueSource` in plaats van
   tientallen bijna-identieke methodes.
4. Ook het positieve geval vastleggen met `assertAccepted`. Zonder dat zou
   een test kunnen slagen doordat álles wordt afgewezen.

### Waarom op `code()` én `kind()`

`BusinessRuleViolation` heeft twee guards die verschillende HTTP-statussen
opleveren:

| Guard | Kind | HTTP |
| --- | --- | --- |
| `require(cond, code)` | `INVALID_INPUT` | 400 |
| `requireState(cond, code)` | `CONFLICT` | 409 |
| — (`notFound(code)`) | `NOT_FOUND` | 404 |

Een regel die van `require` naar `requireState` verschuift, verandert het
API-contract terwijl de rule-code gelijk blijft. Alleen een assertie op
`kind()` vangt dat. Nooit op `getMessage()` asserteren: dat is vrije tekst.

### Fixtures: "one bad field"

`Fixtures.employeeBuilder()` levert per default een geldige medewerker op.
Een test maakt precies één veld ongeldig. Faalt hij, dan staat vast dat het
door dát veld komt.

```java
assertInvalid("contract_hours.max", () ->
        Fixtures.employeeBuilder().contractHours("41.00").build());
```

### Datums altijd relatief

Drie regels gebruiken de systeemklok zonder injecteerbare `Clock`:

| Klasse | Code | Grens |
| --- | --- | --- |
| `EmploymentPeriod` | `hire_date.future` | `now() + 365 dagen` |
| `Absence` | `sick.retroactive` | 14 dagen terug |
| `ExpenseClaim` | `expense_date.future`, `expense_date.stale` | 90 dagen |

Gebruik `LocalDate.now().minusDays(n)` of de constanten in `Fixtures`.
Datumliterals gaan op een willekeurige dag spontaan falen.

## Verificatie van de poort

Een testsuite die niets tegenhoudt is erger dan geen suite: hij geeft valse
zekerheid. Uitgevoerd op 2026-09-10:

| # | Check | Resultaat |
| --- | --- | --- |
| 1 | `mvnw test` groen met testaantal > 0 | **839 tests**, `BUILD SUCCESS` |
| 2 | Selectief draaien (`-Dtest=IbanTest`) | werkt — nodig voor `failure-triager`, fase 5 |
| 3 | **Mutatiecheck**: `iban.nl_length` 18 → 20 | 13 van 26 tests rood, daarna teruggedraaid |
| 4 | `target/` genegeerd door git | `.gitignore:2` |
| 5 | `FixturesTest` bewaakt de testinfrastructuur zelf | 4 tests groen |

Check 3 is het enige echte bewijs dat de poort werkt. Herhaal die bij elke
uitbreiding van de suite.

## Karakteriseringen: vastgelegd, niet gerepareerd

Fase 0 legt bestaand gedrag vast. Onderstaande punten zien er verkeerd uit
maar zijn met een `// KARAKTERISERING:`-comment in de bijbehorende testklasse
vastgelegd zoals ze nu zijn. Repareren gebeurt in fase 1, mét testdekking als
net. Zoek op `KARAKTERISERING` in `backend/src/test` voor de volledige lijst
met code; hieronder de samenvatting per thema.

**Inconsistente `parse(null)`-afhandeling tussen enums:**

| Enum | `parse(null)` |
| --- | --- |
| `ContractType`, `Absence.Type` | gooit een `.missing`-fout |
| `ProjectStatus`, `ProjectMember.Role`, `EntryType` | kiest stilzwijgend een default (DRAFT/MEMBER/REGULAR) |

**Plekken zonder rule-code (NPE of NumberFormatException in plaats van `BusinessRuleViolation`):**

- `Hours.of("acht")` → `NumberFormatException`
- `ContractType.validateRate(null)` bij INTERN/FREELANCE → `NullPointerException`
- `Timesheet.book(..., workDate=null, ...)` → `NullPointerException` (via `IsoWeek.contains`)
- `Timesheet.submit(..., approvedAbsenceHours=null, ...)` → `NullPointerException`
- `Project.changeStatus(null)` → `NullPointerException` (via `Set.of(...).contains(null)`)
- `ExpenseClaim.file(..., category=null, ...)` → `NullPointerException`

**Drie verschillende stapgroottes in één domein:** `Hours` (kwartier, 0,25),
`Employee.contract_hours` (half uur, 0,5), `Absence.hours_per_day` (half uur,
0,5). Geen technisch probleem, wel vermeldenswaard voor wie een generieke
"uren"-validator zou willen bouwen.

**Ontbrekende invarianten / autorisatie, vergeleken met `Timesheet`:**

- `Absence.approve()` heeft geen statuscheck en geen goedkeurder — dubbel
  goedkeuren mag, in tegenstelling tot `Timesheet.approve` (4 regels).
- `ExpenseClaim.decide()` kent geen bevoegdheidscontrole op de beslisser.
- `Employee.assertCanBookOn` wordt door `TimesheetService` niet gebruikt;
  die gebruikt in plaats daarvan `EmployeeSnapshot.canBookOn`.
- `TimeEntry.projectId` wordt niet op null gevalideerd, ondanks `NOT NULL`
  in `V1__schema.sql`.
- `Absence.request()` valideert `type` niet op null, ondanks `NOT NULL` in
  het schema.

**Overig:**

- `Money.of()` rondt af op 2 decimalen (HALF_UP); de constructor weigert
  meer dan 2 decimalen (`money.scale`). Twee ingangen, twee gedragingen.
- `Client.register`: `active=null` → `true`; `Absence.request`:
  `approved=null` → `false`. Tegengestelde defaults voor een vergelijkbaar
  veld.
- `Iban`: de NL-lengte-eis (18 tekens) geldt alleen voor NL; andere landen
  worden enkel op mod-97 gecontroleerd.
- `EmploymentPeriod.hire_date.future`: een indienstdatum mag tot 365 dagen
  vooruit worden vastgelegd.
- `Project.start(..., status=ACTIVE, ...)` mag zonder taken; alleen
  `changeStatus(ACTIVE)` bewaakt `project.no_tasks`.
- `Project.reschedule` bewaakt de projectstatus niet — kan ook op een
  gesloten project.
- `ProjectCode` begrenst het jaartal niet (in tegenstelling tot `IsoWeek`,
  dat 2000-2100 eist).

**Openstaande vragen voor het team:**



## Dekking

Doel is niet 100% coverage, maar: **elke rule-code die een agent kan raken
heeft een test die op `code()` asserteert.** Coverage is het hulpmiddel.

Er zijn 109 `BusinessRuleViolation`-codes, waarvan circa 79 in de
domeinlaag. Voortgang:

| Laag | Klasse | Codes | Status |
| --- | --- | --- | --- |
| `testsupport` | `Violations`, `Fixtures` (+ `FixturesTest`) | — | ✅ fundament |
| `personeel.domain` | `Iban` | 4 | ✅ volledig |
| `shared.domain` | `Money`, `Hours`, `IsoWeek` | 9 | ✅ volledig |
| `personeel.domain` | `EmployeeCode`, `EmailAddress`, `EmploymentPeriod`, `ContractType` | 9 | ✅ volledig |
| `projecten.domain` | `ProjectCode`, `ProjectStatus`, `ProjectMember` | 5 | ✅ volledig |
| `urenregistratie.domain` | `EntryType`, `TimesheetStatus` | 1 | ✅ volledig |
| aggregates | `Employee`, `Timesheet` (+ `TimeEntry`), `Absence`, `Project` (+ `ProjectTask`), `Client`, `ExpenseClaim` | ~45 | ✅ volledig — **839 tests totaal** |
| applicatielaag | `*Service` | ~30 | ⬜ fase 0.5, Persoon B (integratietests, geen unit-scope) |

Alle domeinlaag-codes die via de publieke API van een value object of
aggregate bereikbaar zijn, hebben een test. De ~30 codes in de
applicatielaag (`TimesheetService` vooral) zijn met opzet **niet** hier
getest: die coördineren tussen aggregates (project actief? medewerker in
dienst?) en hebben een Spring-context nodig. Dat is Persoon B, fase 0.5.

## Bewust weggelaten uit fase 0

Om het werk klein te houden zijn drie punten uit de oorspronkelijke
fasering **niet** uitgevoerd:

| Onderdeel | Reden om te laten vallen |
| --- | --- |
| jacoco-coveragedrempel | De mutatiecheck (zie boven) bewijst al dat de poort bijt. Coverage-percentage voegt daar niets aan toe en kost onderhoud (drempel bijstellen bij elke wijziging). |
| spotless | Raakt bij eerste toepassing alle 62 bestanden in `src/main`. Levert nu alleen ruis in de diff op, geen extra zekerheid. |
| checkstyle | Voegt niets toe bovenop 839 gedragstests. Kan alsnog door Persoon C in `ci.yml` als losstaande, optionele check worden toegevoegd. |

De kwaliteitspoort werkt zonder deze drie: `mvnw verify` is het enige dat
nodig is als verplichte CI-check.

## Voor Persoon B en C

**Persoon B** — integratietests en `discovered-rules.md`:

- Naamconventie is `*IT`; failsafe is al geconfigureerd en aan `verify`
  gebonden. Alleen Testcontainers + RestAssired toevoegen.
- `application.yml` heeft **geen Spring-profielen** en geen H2-fallback;
  `ddl-auto: validate` eist dat het schema exact overeenkomt met Flyway.
  Testcontainers Postgres **16** (gelijk aan docker-compose).
- De seed is minimaal en **hardgecodeerd op jaar 2026**: het enige boekbare
  project is `PRJ-2026-001` (ACTIVE), met alleen `EMP-0001` (LEAD) en
  `EMP-0002` (MEMBER) als leden. `EMP-0001` heeft 7,50 u geboekt op
  2026-02-02.
- De ~30 codes in de applicatielaag (`TimesheetService` heeft de meeste)
  zijn alleen via integratietests te raken.
- `Violations` is herbruikbaar voor servicetests; voor HTTP-niveau is een
  variant nodig die op de responsebody asserteert — dat kan pas ná fase 1,
  omdat `RestExceptionHandler` de codes nu weggooit.

**Persoon C** — `ci.yml`:

- Gebruik `./mvnw` (niet `mvn`) en `actions/setup-java` met Temurin 21.
- Cache `~/.m2` **inclusief** `~/.m2/wrapper/dists`.
- Twee stappen: `./mvnw test` (snel, faalt vroeg) en daarna
  `./mvnw verify -DskipTests=false` voor de integratietests. Zo faalt de
  build op een unit-test binnen een minuut in plaats van na Testcontainers.
- Publiceer `backend/target/surefire-reports/*.xml` als testrapport; die
  bevatten de rule-code in de faalmelding, wat `failure-triager` nodig heeft.
- Checkstyle is optioneel toe te voegen als losstaande check (zie "Bewust
  weggelaten uit fase 0"); niet blokkerend voor de eerste groene pipeline.

## Nog niet toegewezen (fase 0, item 6 en 7)

Twee punten uit `agentic-workflow-plan.md` §5 hebben nog **geen eigenaar**.
Ze zijn klein, onafhankelijk van het backend-werk van A/B, en kunnen door wie
als eerste tijd heeft:

- **Item 6 — Frontend-kwaliteitspoort**: vitest + testing-library + eslint +
  prettier + `tsc --noEmit` in `frontend/`. Nu is er alleen `dev`/`build`/
  `preview` in `package.json`.
- **Item 7 — Repo-hygiëne**: `.editorconfig`, `CODEOWNERS`, PR-template met
  een agent-checklist (nodig zodra `pr-author`/`pr-gatekeeper` uit
  `agent-catalogus.md` gebouwd worden).

Zonder deze twee is fase 0 niet volledig af, maar ze blokkeren `ci.yml`
(Persoon C, item 8) niet: die kan starten met alleen backend-checks en later
frontend-stappen toevoegen.

## Status: fase 0 voor Persoon A is afgerond

- ✅ Blok 1-2: Maven wrapper, surefire/failsafe, `Violations`, `Fixtures`, `IbanTest`
- ✅ Blok 3: value-object-tests (`shared`, `personeel`, `projecten`, `urenregistratie`)
- ✅ Blok 4: aggregate-tests (`Employee`, `Timesheet`+`TimeEntry`, `Absence`,
  `Project`+`ProjectTask`, `Client`, `ExpenseClaim`) — **839 tests, `BUILD SUCCESS`**
- ⬜ Blok 5 (jacoco/spotless/checkstyle): **bewust niet gedaan**, zie
  "Bewust weggelaten uit fase 0" hierboven

Niets resteert voor Persoon A binnen fase 0. Vervolgstappen liggen bij
Persoon B (fase 0.5: Testcontainers, RestAssured, `*IT`) en Persoon C
(`ci.yml`, branch protection). Items 6 (frontend) en 7 (repo-hygiëne) uit
`agentic-workflow-plan.md` §5 zijn nog **niet toegewezen** — zie hierboven.
