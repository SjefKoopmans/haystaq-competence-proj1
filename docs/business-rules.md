# Business rules TijdWijs

> Laatst bijgewerkt: 10 september 2026 (fase 1, Persoon A) — 9 afwijkingen
> tussen deze pagina en de code gecorrigeerd, zie "Wijzigingslog" onderaan.
> Status: geverifieerd tegen de domeinlaag. Voor de volledige, machine-
> gegenereerde lijst van rule-codes zie [discovered-rules.md](discovered-rules.md).

Deze pagina beschrijft de belangrijkste validatieregels. Bij twijfel geldt de
implementatie — dat blijft zo; deze pagina volgt de code, niet omgekeerd.

## Medewerkers

- Het personeelsnummer heeft de vorm `EMP-` gevolgd door vier cijfers.
- Het e-mailadres is uniek binnen de organisatie.
- Contracturen liggen tussen 0 en 40 uur, in stappen van een half uur.
  **Let op:** de code controleert alleen de bovengrens (`contract_hours.max`)
  en de stapgrootte (`contract_hours.step`); een waarde van 0 of negatief
  wordt niet apart afgewezen door een expliciete ondergrens-regel.
- Het uurtarief is een bedrag in euro's met twee decimalen.
- Voor stagiairs (`INTERN`) geldt een maximumtarief van €45,00 per uur
  (`intern.rate`); voor zelfstandigen (`FREELANCE`) een minimumtarief van
  €60,00 per uur (`freelance.rate`). Dit was de ontbrekende afspraak uit
  release 3.0.
- Een medewerker is minimaal 16 jaar op de datum van indiensttreding
  (`age.minimum`) én maximaal 70 jaar op vandaag (`age.maximum`) — de
  bovengrens stond niet in deze documentatie.
- De einddatum ligt na de startdatum.
- Het rekeningnummer moet een geldig IBAN zijn (mod-97). Voor NL-IBAN's geldt
  daarnaast een vaste lengte van 18 tekens (`iban.nl_length`); voor andere
  landen wordt alleen de mod-97-controle toegepast.

## Opdrachtgevers en projecten

- De projectcode heeft de vorm `PRJ-<jaar>-<volgnummer>`.
- Een project hoort bij precies één opdrachtgever.
- De einddatum mag leeg blijven (doorlopend project).
- Alleen projecten met status `ACTIVE` mogen geboekt worden.
- Een project kan niet terug van `CLOSED` naar `ACTIVE`.

## Weekstaten

- Een medewerker heeft maximaal één weekstaat per week.
- Weeknummering volgt ISO-8601: week 1 is de week waarin 4 januari valt.
- Statussen: `DRAFT` -> `SUBMITTED` -> `APPROVED` of `REJECTED`.
- Een afgekeurde weekstaat kan opnieuw worden ingediend.
- Een weekstaat wordt goedgekeurd door de leidinggevende of de projectlead,
  nooit door de medewerker zelf (`approver.self`).
- **Indienen kan alleen als de week gedekt is:** geboekte uren plus
  goedgekeurd verlof binnen die week moeten samen minimaal de contracturen
  bereiken (`submit.week_incomplete`). Dit is de sinds release 3.1
  toegevoegde controle uit ticket TW-871 — de oorzaak was onbekend, dit is
  hem.

## Urenregels

- Uren worden geregistreerd in stappen van een kwartier.
- Maximaal **12 uur** per regel (`hours.max`) — niet 10 zoals eerder hier
  stond.
- Maximaal 16 uur per dag, opgeteld over alle weekstaten van die medewerker.
- De datum valt binnen de week van de weekstaat.
- Op een weekstaat met status `SUBMITTED` of `APPROVED` kan niet meer geboekt
  worden.
- **Overwerk** (`entryType=OVERTIME`) mag pas geboekt worden nadat de
  contracturen van die week al vol zijn (`overtime.before_contract_hours`).
  Dit stond nergens beschreven; het is nu wel gedocumenteerd.

## Verlof en verzuim

- Verlofperiodes van dezelfde medewerker mogen niet overlappen.
- Standaard 8 uur per dag; een afwijkend aantal moet tussen 0 (exclusief) en
  8 uur liggen, in stappen van een half uur (`hours_per_day.range`,
  `hours_per_day.step`).
- Een verlofperiode duurt maximaal 60 dagen (`duration.max`) — niet eerder
  gedocumenteerd.
- Ziekmeldingen kunnen met terugwerkende kracht worden ingevoerd, tot
  maximaal 14 dagen terug (`sick.retroactive`) — de grens stond niet hier.

## Declaraties

- Bedragen zijn positief en begrensd per categorie: €5.000,00 voor de meeste
  categorieën, €10.000,00 voor `HARDWARE` (`amount.category_limit`) — niet
  eerder gedocumenteerd.
- **Boven de €25,00 is een bonnummer verplicht** (`receipt_reference.required`).
  Deze pagina noemde eerder €50,00; de code gebruikt €25,00. Neem dit mee
  naar de eigenaar van declaratiebeleid als €50,00 de bedoelde grens was —
  tot dan is de code leidend.
- Het btw-tarief is 0%, 9% of 21% (`vat_rate.unknown`). Voor de categorieën
  `MEALS` en `SOFTWARE` ligt het tarief vast op respectievelijk 9% en 21%
  (`vat_rate.category`) — niet eerder gedocumenteerd.
- Declaraties ouder dan **90 dagen** worden niet meer vergoed
  (`expense_date.stale`); een declaratie met een datum in de toekomst wordt
  ook afgewezen (`expense_date.future`).
- Bij een andere valuta dan EUR is een toelichting verplicht
  (`description.foreign_currency`) — niet eerder gedocumenteerd.

## Bekende openstaande punten

1. ~~De regels rond overwerk staan nergens beschreven.~~ Opgelost, zie
   "Urenregels" hierboven.
2. ~~Sinds release 3.1 controleert het systeem iets extra's bij het indienen
   van een weekstaat... Ticket TW-871.~~ Opgelost, zie "Weekstaten" hierboven
   (`submit.week_incomplete`).
3. Foutmeldingen bevatten sinds fase 1 een machine-leesbare rule-code
   (RFC 9457 `code`-veld in de HTTP-respons), maar nog geen mens-leesbare
   veldinformatie in de body. Blijft op de backlog.
4. Niemand weet meer waarom een medewerker aan een project gekoppeld moet
   zijn voordat er geboekt kan worden (`employee.not_member`). Het staat wel
   in de code; functionele reden nog te achterhalen bij de opvolger van
   R. Mulder.

## Wijzigingslog (fase 1)

Deze 9 punten weken af tussen deze pagina en de code op 2026-09-10; ze zijn
hierboven gecorrigeerd. Zie ook `docs/agentic-workflow-plan.md` §10 D1.

| # | Onderwerp | Was hier | Is volgens de code |
| --- | --- | --- | --- |
| 1 | Bonnummer-drempel | €50,00 | €25,00 (`receipt_reference.required`) |
| 2 | Contracturen | "tussen 0 en 40" | alleen bovengrens + stap van 0,5 gecontroleerd |
| 3 | Maximumleeftijd | niet genoemd | 70 jaar (`age.maximum`) |
| 4 | Tarief per contractvorm | TODO/onbekend | `INTERN` ≤ €45, `FREELANCE` ≥ €60 |
| 5 | Max uren per regel | 10 | 12 (`hours.max`) |
| 6 | Declaratie-vervaltermijn | "drie maanden" | 90 dagen exact, plus toekomst-check en categorie-VAT |
| 7 | Overwerk | "nergens beschreven" | `overtime.before_contract_hours` |
| 8 | TW-871 (submit-conflict) | onbekende oorzaak | `submit.week_incomplete` |
| 9 | Verlof standaard | "8 uur per dag" | range/stap + max 60 dagen + ziek terugwerkend ≤14 dagen |
