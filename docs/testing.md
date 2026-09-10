# Testen — TijdWijs backend

> Status: fase 0 blok 1-2 afgerond. Eigenaar: Persoon A.
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
| 1 | `mvnw test` groen met testaantal > 0 | 30 tests, `BUILD SUCCESS`, ~2 s |
| 2 | Selectief draaien (`-Dtest=IbanTest`) | werkt — nodig voor `failure-triager`, fase 5 |
| 3 | **Mutatiecheck**: `iban.nl_length` 18 → 20 | 13 van 26 tests rood, daarna teruggedraaid |
| 4 | `target/` genegeerd door git | `.gitignore:2` |
| 5 | `FixturesTest` bewaakt de testinfrastructuur zelf | 4 tests groen |

Check 3 is het enige echte bewijs dat de poort werkt. Herhaal die bij elke
uitbreiding van de suite.

## Karakteriseringen: vastgelegd, niet gerepareerd

Fase 0 legt bestaand gedrag vast. Onderstaande punten zien er verkeerd uit
maar zijn met een `// KARAKTERISERING:`-comment vastgelegd zoals ze nu zijn.
Repareren gebeurt in fase 1, mét testdekking als net.

**Openstaande vragen voor het team:**

| # | Klasse | Waargenomen gedrag | Vraag |
| --- | --- | --- | --- |
| 1 | `Iban` | Lengtecontrole geldt alleen voor NL; andere landen worden enkel op mod-97 gecheckt | Bewust? |
| 2 | `Iban` | `JpaConverter` valideert bij lezen — corrupte kolomdata gooit een domeinfout | Gewenst bij inlezen bestaande data? |
| 3 | `Hours` | 0 uur is ongeldig (`hours.positive`) | Moet 0 kunnen, bijv. bij correctieboekingen? |
| 4 | `Absence` | `approve()` heeft geen statuscheck; dubbel goedkeuren mag | Ontbrekende invariant? |
| 5 | `Employee` | `assertCanBookOn` wordt niet gebruikt door `TimesheetService`, die gebruikt `EmployeeSnapshot.canBookOn` | Dode code of gemiste controle? |
| 6 | `TimeEntry` | `projectId` wordt niet op null gevalideerd | Ontbrekende `require`? |
| 7 | `ProjectStatus` | `parse(null)` geeft `DRAFT`, geen fout | Bewuste default? |
| 8 | `Money` | `of()` rondt af op 2 decimalen, `new Money()` weigert ze (`money.scale`) | Twee ingangen met verschillend gedrag — bewust? |
| 9 | `EmploymentPeriod` | Toekomstige indienstdatum mag tot 365 dagen vooruit | Bewust? |

Punten 1-2 zijn tijdens het schrijven van `IbanTest` vastgesteld en getest;
3-9 komen uit de code-inventarisatie en worden in blok 3-4 met tests
vastgelegd.

## Dekking

Doel is niet 100% coverage, maar: **elke rule-code die een agent kan raken
heeft een test die op `code()` asserteert.** Coverage is het hulpmiddel.

Er zijn 109 `BusinessRuleViolation`-codes, waarvan circa 79 in de
domeinlaag. Voortgang:

| Laag | Klasse | Codes | Status |
| --- | --- | --- | --- |
| `testsupport` | `Violations`, `Fixtures` (+ `FixturesTest`) | — | ✅ fundament |
| `personeel.domain` | `Iban` | 4 | ✅ volledig |
| `shared.domain` | `Money`, `Hours`, `IsoWeek` | 9 | ⬜ blok 3 |
| `personeel.domain` | `EmployeeCode`, `EmailAddress`, `EmploymentPeriod`, `ContractType` | 9 | ⬜ blok 3 |
| `projecten.domain` | `ProjectCode`, `ProjectStatus`, `ProjectMember`, `ProjectTask` | 6 | ⬜ blok 3 |
| `urenregistratie.domain` | `EntryType`, `TimeEntry` | 6 | ⬜ blok 3 |
| aggregates | `Employee`, `Timesheet`, `Absence`, `Project`, `Client`, `ExpenseClaim` | ~45 | ⬜ blok 4 |
| applicatielaag | `*Service` | ~30 | ⬜ fase 0.5, Persoon B |

Jacoco komt in blok 5, ná blok 4: eerst meten, dan de drempel vijf punten
onder de werkelijke waarde zetten. Een drempel die direct rood is, zet
iemand binnen een dag uit.

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

## Nog te doen in fase 0 (Persoon A)

- Blok 3: value-object-tests (`shared`, `personeel`, `projecten`, `urenregistratie`)
- Blok 4: aggregate-tests (6 klassen)
- Blok 5: jacoco-drempel, spotless (in één losse opmaak-commit — raakt alle
  62 bestanden), checkstyle op `severity=warning`
