# Branch protection op `main`

> Fase 0.9 uit [agentic-workflow-plan.md](agentic-workflow-plan.md).
> Vereist **admin**-rechten op `SjefKoopmans/haystaq-competence-proj1`.

## Waarom dit geen detail is

`ci.yml` controleert. Branch protection is wat die controle *bindend* maakt.
Zonder deze instelling kan een PR met rode checks en zonder review gemerged
worden — en dan is het besluit "geen auto-merge, altijd menselijke review" een
afspraak in een document in plaats van een grendel in het systeem.

De agentic keten leunt hier direct op: `pr-gatekeeper` blokkeert bij
`REQUEST_CHANGES` alleen als GitHub die review afdwingt.

## Instellen

```bash
bash scripts/setup-branch-protection.sh
```

Eerst kijken wat er wordt verstuurd:

```bash
DRY_RUN=true bash scripts/setup-branch-protection.sh
```

Zonder GitHub CLI: **Settings → Branches → Add branch protection rule**, patroon
`main`, en zet daar de tabel hieronder over.

## Wat er wordt gezet, en waarom

| Instelling | Waarde | Reden |
| --- | --- | --- |
| Require a pull request before merging | aan | Direct pushen naar `main` sluit alle controles uit |
| Required approvals | 1 | Het menselijke checkpoint uit het plan |
| Require review from Code Owners | aan | Zonder dit doet `.github/CODEOWNERS` niets |
| Dismiss stale approvals | aan | Een goedkeuring geldt voor de diff die is gelezen, niet voor de volgende |
| Require approval of the most recent push | aan | Anders kan de agent ná de review nog ongezien code toevoegen |
| Require status checks to pass | `Kwaliteitspoort` | De samenvattende job uit `ci.yml`; komt er een job bij, dan blijft deze instelling kloppen |
| Require branches to be up to date | aan | Groen op een verouderde basis zegt niets |
| Require conversation resolution | aan | Een openstaande `blocker` van de gatekeeper mag niet stil verdwijnen |
| Require linear history | aan | Leesbare historie; squash- of rebase-merge |
| Allow force pushes / deletions | uit | Historie van `main` is onaantastbaar |
| Do not allow bypassing (enforce admins) | **uit** | Bewust: bij een vastgelopen pilot moet een mens er nog bij kunnen. Zet dit aan zodra de pipeline draait |

## Volgorde

De check `Kwaliteitspoort` bestaat pas voor GitHub nadat `ci.yml` één keer heeft
gedraaid. Dus:

1. Merge de PR met `ci.yml` (deze PR).
2. Wacht tot de workflow één keer op `main` heeft gedraaid.
3. Draai `scripts/setup-branch-protection.sh`.

Andersom krijg je "check not found" en blokkeert het alsnog alles.

## Extra instellingen buiten dit script

In **Settings → General**:

- **Allow merge commits: uit**, squash of rebase aan — past bij
  `required_linear_history`.
- **Automatically delete head branches: aan** — de keten maakt per ticket een
  branch; die hoeven niet te blijven staan.

In **Settings → Actions → General**:

- **Workflow permissions**: `Read repository contents` als standaard. De
  agent-workflows vragen zelf om meer via `permissions:` per job.
- **Allow GitHub Actions to create and approve pull requests**: creëren mag,
  **approven niet**. Een keten die zijn eigen PR goedkeurt heeft geen poort.

## Verificatie

```bash
gh api /repos/SjefKoopmans/haystaq-competence-proj1/branches/main/protection \
  | jq '{checks: .required_status_checks.checks, reviews: .required_pull_request_reviews}'
```

Negatieve test — hoort te falen:

```bash
git push origin main --force
```
