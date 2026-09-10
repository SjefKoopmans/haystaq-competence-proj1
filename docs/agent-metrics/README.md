# Agent-metrics

> Fase 5 uit [agentic-workflow-plan.md](../agentic-workflow-plan.md).

Eén JSON-bestand per agent-run, geschreven door `metrics-collector`. Daaruit
rolt wekelijks [`REPORT.md`](REPORT.md) — gegenereerd, niet met de hand
bijgewerkt.

```
docs/agent-metrics/
├── schema.json                 het contract
├── REPORT.md                   gegenereerd door scripts/metrics-rollup.mjs
└── 2026-09-09-KAN-41.json      één run
```

## Waarvoor dit dient

Twee besluiten hangen hieraan, en aan niets anders:

1. **Mag het autonomiedomein groeien?** Het percentage runs zonder menselijke
   correctie is de maat. Blijft dat laag, dan is verbreden van de scope
   vertrouwen uitgeven dat er niet is.
2. **Levert `pr-gatekeeper` netto tijdwinst?** Niet te zien aan hoeveel hij
   vindt, wel aan wat hij **mist** (`false_negatives`) en waar hij **te streng**
   is (`false_positives`). Zonder die twee getallen weet je alleen dat hij
   bezig is.

## Voorbeeld

```json
{
  "schema_version": 1,
  "issue_key": "KAN-41",
  "ticket_class": "bugfix",
  "run_id": "17012345678",
  "run_url": "https://github.com/SjefKoopmans/haystaq-competence-proj1/actions/runs/17012345678",
  "branch": "agent/KAN-41",
  "pr_number": 12,
  "pr_url": "https://github.com/SjefKoopmans/haystaq-competence-proj1/pull/12",
  "started_at": "2026-09-09T09:12:00Z",
  "finished_at": "2026-09-09T09:31:00Z",
  "duration_seconds": 1140,
  "phases": [
    { "name": "ticket-refiner", "status": "ok", "duration_seconds": 95 },
    { "name": "domain-implementer", "status": "ok", "duration_seconds": 420 },
    { "name": "test-author", "status": "ok", "duration_seconds": 260 },
    { "name": "pr-gatekeeper", "status": "ok", "duration_seconds": 180 }
  ],
  "tokens": { "input": 412000, "output": 38000, "total": 450000, "estimated_cost_eur": 2.85 },
  "retries": 0,
  "ci_first_attempt_green": true,
  "diff": { "files_changed": 4, "lines_added": 96, "lines_removed": 12, "bounded_contexts": ["urenregistratie"] },
  "outcome": "merged",
  "escalated": false,
  "escalation_reason": null,
  "gatekeeper": {
    "advice": "APPROVE",
    "confidence": "hoog",
    "rounds": 0,
    "findings": { "blocker": 0, "belangrijk": 1, "suggestie": 2 },
    "false_negatives": 1,
    "false_positives": 0
  },
  "human_correction_commits": 0,
  "merged_at": "2026-09-09T11:04:00Z",
  "notes": null
}
```

## `null` versus `0`

Deze twee betekenen iets heel anders en de rollup rekent er ook anders mee:

| Waarde | Betekenis |
| --- | --- |
| `0` | Geteld, en het waren er nul |
| `null` | Nog niet te weten — bijvoorbeeld correcties vóór de merge |

Een `0` invullen waar `null` hoort, laat de pipeline er beter uitzien dan hij
is. Dat is de enige manier waarop dit rapport waardeloos kan worden.

`human_correction_commits`, `gatekeeper.false_negatives` en
`gatekeeper.false_positives` staan tijdens de run altijd op `null` en worden na
de menselijke review ingevuld.

## Commando's

```bash
node scripts/metrics-rollup.mjs --validate
```

```bash
node scripts/metrics-rollup.mjs
```

```bash
node scripts/metrics-rollup.mjs --days 7
```

De wekelijkse workflow `.github/workflows/agent-report.yml` draait het derde
commando elke maandagochtend en opent een PR als het rapport wijzigt. Handmatig
kan het ook: **Actions → Agent report → Run workflow**, of `/agent-report`.
