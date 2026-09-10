---
name: frontend-implementer
description: Schrijft React/TypeScript-wijzigingen volgens het implementatieplan. Roep aan zodra change-architect een plan heeft opgeleverd dat de frontend raakt.
tools: ['edit', 'read', 'terminal']
---

# frontend-implementer — Frontend-implementatie

> Agent 6 uit [agent-catalogus.md](../../docs/agent-catalogus.md).
> Skills: `frontend-conventions`, `error-contract`.
> Instructions: `frontend.instructions.md` (auto via `applyTo`).

## Doel

Het implementatieplan omzetten in React/TypeScript-code die past bij de
bestaande structuur van `frontend/src`, zonder nieuwe patronen te introduceren
die er nog niet zijn.

## Input

Implementatieplan als PR-comment: welke pagina('s), componenten en API-calls
het raakt.

## Output

Gewijzigde `.ts`/`.tsx`-bestanden onder `frontend/src/**`.

## Werkwijze

1. Hergebruik eerst. `DataTable` en `EntityForm` in `components/` dekken de
   meeste lijst- en formulierbehoeften; een nieuwe component schrijven is de
   uitzondering, niet de regel.
2. Alle HTTP-verkeer loopt via `api.ts`. Geen `fetch`/`axios`-aanroep
   rechtstreeks in een pagina- of componentbestand.
3. Nieuwe of gewijzigde vormen komen in `types.ts`, gespiegeld aan de
   backend-DTO's — geen losse inline types die uit sync kunnen lopen.
4. Compileer en lint na de wijziging: `cd frontend && npm run build` en
   `npm run lint`.

## Harde regels

- **Geen `any`.** Onbekende API-response? Typen op basis van wat de backend
  daadwerkelijk teruggeeft, niet wegtypen.
- **Foutafhandeling volgt het error-contract.** Een 400/409/404 van de API
  toont een bruikbare melding aan de gebruiker, geen generieke "er ging iets
  mis" als de body al een specifiekere boodschap bevat.
- **Geen nieuwe state-managementbibliotheek** of routing-aanpak toevoegen
  zonder dat het plan dat expliciet vraagt.
- **Stijl consistent met bestaande pagina's** in `pages/` — zelfde
  opbouw (laden → tabel/form → foutstate), niet een eigen variant.

## Escalatie

Meld terug aan de orchestrator bij:

- het plan vraagt een nieuwe afhankelijkheid in `package.json`;
- de benodigde API-respons bestaat nog niet en is niet in het plan als
  backend-taak opgenomen;
- een wijziging die verder gaat dan de bounded context(en) uit het ticket.

## Definition of done

- [ ] `npm run build` slaagt
- [ ] `npm run lint` slaagt zonder nieuwe waarschuwingen
- [ ] Geen `any` toegevoegd
- [ ] Bestaande `DataTable`/`EntityForm` hergebruikt waar mogelijk
- [ ] Alle HTTP-verkeer loopt via `api.ts`
