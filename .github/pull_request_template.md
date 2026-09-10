<!--
Titel volgens Conventional Commits: feat|fix|docs|test|refactor|chore(scope): beschrijving
Voorbeeld: fix(urenregistratie): weekstaat afwijzen bij urenregel buiten ISO-week
-->

## Ticket

<!-- Verplicht. Zonder link is niet te controleren of dit het gevraagde probleem oplost. -->
Jira: [KAN-000](https://haystaqteam3.atlassian.net/browse/KAN-000)

## Wat verandert er, en waarom

<!-- In termen van gedrag en van het ticket, niet in termen van bestanden. -->

## Review-focus

<!--
Waar moet de reviewer zijn tijd aan besteden? Noem bestand:regel.
"Kijk overal even naar" is geen review-focus.
-->

- 

## Wat er NIET is gedaan, en waarom

<!-- Bewust weggelaten scope, bekende beperkingen, opgeruimde-maar-niet-opgeloste zaken. -->

- 

## Wat ik niet heb kunnen verifiëren

<!-- Expliciet zijn hierover is waardevoller dan doen alsof alles gedekt is. -->

- 

---

## Checklist - auteur

- [ ] Titel volgt Conventional Commits
- [ ] Ticket gelinkt en de acceptatiecriteria staan hierboven afgevinkt
- [ ] `cd backend && ./mvnw -B verify` groen
- [ ] `cd frontend && npm run verify` groen (`tsc --noEmit` + lint + test + build)
- [ ] Geen ongerelateerde formatting in de diff
- [ ] Geen secrets, tokens, hardcoded URL's of connectiestrings

## Checklist - business rules

- [ ] Nieuwe of gewijzigde regel heeft een test die op de **rule-code**
      asserteert (`ex.code()`), niet alleen op HTTP-status
- [ ] Grenswaarden getest: min−1, min, max, max+1
- [ ] Geen bestaande test aangepast; is dat wél gebeurd, onderbouw hieronder
      waarom de oude verwachting fout was:
      <!-- onderbouwing: -->
- [ ] `docs/discovered-rules.md` is bijgewerkt door de generator, niet met de hand
- [ ] Migratie is forward-only; bestaande `V*.sql` is niet gewijzigd

## Checklist - architectuur

- [ ] Geen import over een bounded-context-grens heen
- [ ] Geen Spring-import in `**/domain/**`
- [ ] Geen setter op een aggregate; wijzigen gebeurt via gedrag
- [ ] Regel staat in de juiste laag (aggregate als het aggregate het zelf kan
      zien, anders applicatielaag)

## Agent-disclosure

<!--
Verplicht als een agent aan deze PR heeft meegeschreven. Zet dan ook het label
`agent-generated`. Bij een volledig handgeschreven PR: "n.v.t.".
-->

- Agents die hebben meegeschreven: 
- Menselijke correcties na de agent: 
- CI bij eerste poging: groen / rood
