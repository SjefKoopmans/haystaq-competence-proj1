# haystaq-competence-proj1 - TijdWijs

> Competence-dag, opdracht 1: **"Handmatig testdata maken kost veel tijd."**
> De opdracht staat in [MISSION.md](MISSION.md). Dit bestand beschrijft de applicatie.

TijdWijs is een urenregistratiesysteem voor een detacheringsbureau: medewerkers,
opdrachtgevers, projecten, taken, weekstaten, urenregels, verlof en declaraties.
De applicatie is bewust gebouwd met veel formulieren, veel verschillende datatypes,
strenge en deels ongedocumenteerde business rules, en **nietszeggende foutmeldingen**.

## Stack

| Laag | Technologie |
| --- | --- |
| Backend | Java 21, Spring Boot 3.4, Spring Data JPA, Flyway |
| Database | PostgreSQL 16 (eigen database, alleen voor deze applicatie) |
| Frontend | React 18 + TypeScript, Vite, uitgeserveerd door nginx |
| Architectuur | Domain Driven Design, modulaire monoliet met vier bounded contexts |

Zie [docs/architecture.md](docs/architecture.md) voor de indeling in contexten,
aggregates en poorten.

## Snel starten

Vereist: Docker Desktop (of Docker Engine + Compose v2). Verder niets - Java,
Maven en Node draaien allemaal in de build-containers.

```bash
docker compose up -d --build
```

De eerste build duurt een paar minuten (Maven en npm halen dependencies op).
Daarna:

- UI: <http://localhost:3001>
- API: <http://localhost:8081/api/employees>
- Health: <http://localhost:8081/actuator/health>
- Postgres: `localhost:5433`, database `tijdwijs`, gebruiker `tijdwijs`, wachtwoord `tijdwijs`

Poorten bezet? Maak een `.env` naast `docker-compose.yml`:

```bash
FRONTEND_PORT=4001
BACKEND_PORT=9081
DB_PORT=6433
```

Stoppen inclusief database:

```bash
docker compose down -v
```

## Belangrijk om te weten

1. **De seed is minimaal.** Twee medewerkers, twee opdrachtgevers, twee projecten,
   een halfvolle weekstaat. Alles wat je nodig hebt om echt te testen moet je
   zelf genereren.
2. **Foutmeldingen zeggen niets.** De API antwoordt met
   `400 {"error":"invalid input"}` of `409 {"error":"conflict"}`. Welk veld of
   welke regel het probleem is, staat er niet bij. Dat is precies het probleem
   dat je gaat oplossen.
3. **Onverwachte fouten geven een referentie.** `500 {"error":"internal error","ref":"a1b2c3d4"}`.
   De details staan in de logs: `docker compose logs backend | grep <ref>`.
4. **De documentatie klopt niet helemaal.** [docs/business-rules.md](docs/business-rules.md)
   is, net als in het echt, onvolledig en op punten verouderd.
5. **De database staat open** op poort 5433, zodat je het schema kunt
   introspecteren en data kunt bulk-laden zonder door de API te hoeven.

## Handige commando's

```bash
docker compose logs -f backend
```

```bash
docker compose exec db psql -U tijdwijs -d tijdwijs -c "\dt"
```

```bash
curl -X POST http://localhost:8081/api/admin/reset
```

`admin/reset` draait Flyway clean + migrate: de database gaat terug naar de
minimale seed. Handig als je generator de omgeving onbruikbaar heeft gemaakt.

### Escape hatch voor facilitators

Zet `DEBUG_RULES=true` in `.env` en herstart de backend. De applicatie logt dan
per afwijzing de interne redencode (bijvoorbeeld `iban.mod97` of
`submit.week_incomplete`). Bedoeld om een vastgelopen groepje vlot te trekken -
niet om de opdracht mee te beginnen.

## API in het kort

Alle JSON is camelCase. Er is geen authenticatie.

| Methode | Pad | Doel |
| --- | --- | --- |
| GET/POST | `/api/clients` | Opdrachtgevers |
| GET/POST/PATCH | `/api/employees[/{id}]` | Medewerkers |
| GET/POST/PATCH | `/api/projects[/{id}]` | Projecten |
| POST | `/api/projects/{id}/tasks` | Taak toevoegen |
| POST | `/api/projects/{id}/members` | Medewerker koppelen |
| GET/POST | `/api/timesheets[/{id}]` | Weekstaten |
| POST | `/api/timesheets/{id}/entries` | Uren boeken |
| DELETE | `/api/time-entries/{id}` | Urenregel verwijderen |
| POST | `/api/timesheets/{id}/submit\|approve\|reject` | Statusovergangen |
| GET/POST/DELETE | `/api/absences[/{id}]` | Verlof en verzuim |
| GET/POST | `/api/expenses` | Declaraties |
| POST | `/api/expenses/{id}/transitions` | Declaratie indienen, goedkeuren, betalen |
| GET | `/api/reports/summary`, `/api/reports/weekly` | Rapportage (leesmodel) |
| POST | `/api/admin/reset` | Database terug naar de seed |

Voorbeelden met curl staan in [docs/api.md](docs/api.md).

## Lokaal draaien zonder Docker (optioneel)

```bash
docker compose up -d db
```

```bash
cd backend && DB_PORT=5433 DB_HOST=localhost mvn spring-boot:run
```

```bash
cd frontend && npm install && npm run dev
```

De Vite-devserver draait op <http://localhost:5173> en proxyt `/api` naar
`http://localhost:8081`.

## MCP-servers voor GitHub en Jira

`.mcp.json` bevat twee MCP-servers, zodat je AI-tooling bij de repo en het
KAN-bord kan. Ze draaien in Docker, dus identiek op Windows, macOS en Linux.

Je secrets komen in **`.env.mcp`** in de projectmap. Dat bestand staat in
`.gitignore` en wordt nooit gecommit. Iedereen maakt zijn eigen versie:

```bash
cp .env.mcp.example .env.mcp
```

Vul daarna de drie regels in:

| Variabele | Waar haal je die |
| --- | --- |
| `GITHUB_PERSONAL_ACCESS_TOKEN` | <https://github.com/settings/tokens/new> - een **classic** token met scope `repo` |
| `JIRA_USERNAME` | Het e-mailadres waarmee je op Jira inlogt |
| `JIRA_API_TOKEN` | <https://id.atlassian.com/manage-profile/security/api-tokens> |

> Voor GitHub moet het een **classic** token zijn. Een fine-grained token is
> gebonden aan je eigen account of een organisatie, en deze repo staat op het
> persoonlijke account van een teamlid - die kun je daar niet selecteren.

Herstart daarna je editor volledig. In Claude Code controleer je met `/mcp` of
`github` en `jira` verbonden zijn.

Ontbreekt het bestand, dan zegt de server dat meteen:
`docker: --env-file: open .env.mcp: The system cannot find the file specified.`
