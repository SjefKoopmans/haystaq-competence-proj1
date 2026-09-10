# Jira Automation → GitHub

> Fase 4.1 uit [agentic-workflow-plan.md](agentic-workflow-plan.md).
> De Jira-kant van de trigger. De GitHub-kant staat in
> [`.github/workflows/agent-delivery.yml`](../.github/workflows/agent-delivery.yml)
> en [agent-pipeline.md](agent-pipeline.md).
>
> **Instellen vereist Jira-projectadmin op KAN** (openstaand punt A4).

## Wat er gebeurt

```text
Ticket krijgt label `agent-ready`
  → Jira Automation rule
  → POST https://api.github.com/repos/SjefKoopmans/haystaq-competence-proj1/dispatches
  → GitHub repository_dispatch (event_type: jira-agent-request)
  → workflow agent-delivery.yml
```

## Trigger: label, geen nieuwe status

Het plan noemt twee opties: een nieuwe status "Ready for Agent" of het label
`agent-ready`. **Advies: het label.**

Het bord KAN is team-managed en heeft vier statussen — `To Do` (10004),
`In Progress` (10005), `In Review` (10006), `Done` (10007). Een status
toevoegen verandert het bord voor iedereen die er niets mee te maken heeft, en
je moet hem er weer uit slopen als de pilot stopt. Een label plakt en laat weer
los.

## De rule, veld voor veld

**Automation → Create rule** in projectinstellingen van KAN.

| Onderdeel | Waarde |
| --- | --- |
| Trigger | *Work item updated* → veld `Labels` |
| Condition 1 | Labels *contains* `agent-ready` |
| Condition 2 | Labels *does not contain* `needs-human` |
| Condition 3 | Status *is not* `Done` |
| Action | *Send web request* |

**Send web request:**

| Veld | Waarde |
| --- | --- |
| Web request URL | `https://api.github.com/repos/SjefKoopmans/haystaq-competence-proj1/dispatches` |
| HTTP method | `POST` |
| Web request body | Custom data |
| Headers | `Accept: application/vnd.github+json`<br>`Authorization: Bearer <token>`<br>`X-GitHub-Api-Version: 2022-11-28` |

Custom data:

```json
{
  "event_type": "jira-agent-request",
  "client_payload": {
    "issue_key": "{{issue.key}}",
    "issue_type": "{{issue.issueType.name}}",
    "triggered_by": "jira-automation"
  }
}
```

Meer dan dit hoort er niet in. De workflow haalt de ticketinhoud zelf op via de
Atlassian MCP; de payload is een **verwijzing**, geen gegevensoverdracht. Dat
scheelt gedoe met AVG (C5) en voorkomt dat ticketinhoud in GitHub-logs belandt.

Zet daarna een tweede action: *Edit work item* → verwijder label `agent-ready`.
Anders vuurt elke volgende labelwijziging de rule opnieuw af.

## Het token in de rule

`repository_dispatch` vereist schrijfrechten op de repo:

- **Fine-grained PAT** (voorkeur): alleen deze repository, permission
  **Contents: Read and write**. Meer is niet nodig.
- **Classic token**: scope `repo`. Dat is fors ruimer — alleen doen als
  fine-grained niet lukt.

Jira Automation bewaart het token in de rule zelf; er is geen secrets-kluis.
Gevolgen die je moet accepteren:

1. Iedereen met projectadmin op KAN kan de rule openen. De waarde staat
   gemaskeerd, maar de rule kan wel worden aangepast om ergens anders heen te
   posten.
2. Zet een **verloopdatum** op het token en agendeer de vervanging.
3. Gebruik het token van het service-account (`svc-tijdwijs-agent`), niet je
   persoonlijke token. Zie [mcp-setup.md](mcp-setup.md).

> **Openstaand (A6).** Mag Jira uitgaande webhooks naar `api.github.com`
> sturen? Bij een netwerkbeleid dat dat blokkeert, valt deze route weg en blijft
> `workflow_dispatch` over: dan start een mens de run met de issue key.

## Testen zonder Jira

De workflow accepteert ook een handmatige start — **Actions → Agent delivery →
Run workflow**, met de issue key als invoer. Gebruik dat om de keten te testen
voordat je de rule aanzet.

De webhook zelf test je met dezelfde POST die Jira zou doen:

```bash
curl -X POST \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer $GH_TOKEN" \
  https://api.github.com/repos/SjefKoopmans/haystaq-competence-proj1/dispatches \
  -d '{"event_type":"jira-agent-request","client_payload":{"issue_key":"KAN-42"}}'
```

Een `204 No Content` betekent dat GitHub het event heeft aangenomen. Staat
`AGENT_ENABLED` op `false`, dan stopt de run daarna alsnog binnen een minuut —
dat is de bedoeling en meteen een goede eerste test.

## Volgorde van aanzetten

1. `AGENT_ENABLED=false` (staat standaard zo).
2. Rule aanmaken, label op een testticket zetten, kijken of de run *start* en
   netjes op de kill switch afketst.
3. Keten droog testen via `workflow_dispatch`.
4. Pas daarna `AGENT_ENABLED=true`.

Andersom zet je een onbewezen keten los op echte tickets.

## Wat de rule níet doet

- **Geen ticketcreatie, geen sprintwijziging, geen story points.** De rule
  stuurt alleen een signaal; alles wat Jira daarna te zien krijgt komt van
  `jira-scribe`, en die heeft dezelfde beperkingen (plan §8).
- **Geen transitie.** De status wordt door `jira-scribe` gezet, niet door de
  rule. Twee schrijvers op hetzelfde veld levert een racesituatie op die je op
  een slechte dag niet wilt debuggen.
