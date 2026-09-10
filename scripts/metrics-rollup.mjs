#!/usr/bin/env node
/**
 * Metrics-rollup (fase 5).
 *
 *   node scripts/metrics-rollup.mjs --validate   valideer alleen
 *   node scripts/metrics-rollup.mjs              schrijf docs/agent-metrics/REPORT.md
 *   node scripts/metrics-rollup.mjs --days 7     alleen de laatste 7 dagen
 *
 * Bewust zonder dependencies: dit script draait in een workflow die verder
 * niets installeert, en een rollup die zelf een npm-install nodig heeft is een
 * rollup die op een slechte dag niet draait.
 */

import { readFileSync, writeFileSync, readdirSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const DIR = join(ROOT, 'docs', 'agent-metrics');
const SCHEMA = JSON.parse(readFileSync(join(DIR, 'schema.json'), 'utf8'));

const args = process.argv.slice(2);
const validateOnly = args.includes('--validate');
const daysIndex = args.indexOf('--days');
const days = daysIndex >= 0 ? Number(args[daysIndex + 1]) : null;

/* ---------------------------------------------------------------- validatie */

/** Kleine validator voor precies de constructies die schema.json gebruikt. */
function validate(value, schema, path, errors) {
  if (schema.const !== undefined && value !== schema.const) {
    errors.push(`${path}: verwacht ${JSON.stringify(schema.const)}`);
    return;
  }
  if (schema.enum && !schema.enum.includes(value)) {
    errors.push(`${path}: ${JSON.stringify(value)} zit niet in enum`);
    return;
  }

  const types = schema.type ? [].concat(schema.type) : null;
  if (types) {
    const actual =
      value === null ? 'null' : Array.isArray(value) ? 'array'
      : Number.isInteger(value) ? 'integer' : typeof value;
    const ok = types.some((t) => t === actual || (t === 'number' && actual === 'integer'));
    if (!ok) {
      errors.push(`${path}: type ${actual}, verwacht ${types.join('|')}`);
      return;
    }
    if (value === null) return;
  }

  if (typeof value === 'number') {
    if (schema.minimum !== undefined && value < schema.minimum) {
      errors.push(`${path}: ${value} < minimum ${schema.minimum}`);
    }
    if (schema.maximum !== undefined && value > schema.maximum) {
      errors.push(`${path}: ${value} > maximum ${schema.maximum}`);
    }
  }

  if (typeof value === 'string') {
    if (schema.pattern && !new RegExp(schema.pattern).test(value)) {
      errors.push(`${path}: voldoet niet aan patroon ${schema.pattern}`);
    }
    if (schema.maxLength && value.length > schema.maxLength) {
      errors.push(`${path}: langer dan ${schema.maxLength} tekens`);
    }
  }

  if (Array.isArray(value) && schema.items) {
    value.forEach((item, i) => validate(item, schema.items, `${path}[${i}]`, errors));
  }

  if (value && typeof value === 'object' && !Array.isArray(value) && schema.properties) {
    for (const key of schema.required ?? []) {
      if (!(key in value)) errors.push(`${path}.${key}: verplicht veld ontbreekt`);
    }
    for (const [key, item] of Object.entries(value)) {
      if (!schema.properties[key]) {
        if (schema.additionalProperties === false) {
          errors.push(`${path}.${key}: onbekend veld`);
        }
        continue;
      }
      validate(item, schema.properties[key], `${path}.${key}`, errors);
    }
  }
}

/* ------------------------------------------------------------------ inlezen */

function loadRuns() {
  if (!existsSync(DIR)) return [];
  const cutoff = days ? Date.now() - days * 86400000 : null;
  const runs = [];
  const problems = [];

  for (const file of readdirSync(DIR).sort()) {
    if (!file.endsWith('.json') || file === 'schema.json') continue;
    const full = join(DIR, file);
    let data;
    try {
      data = JSON.parse(readFileSync(full, 'utf8'));
    } catch (error) {
      problems.push(`${file}: geen geldige JSON (${error.message})`);
      continue;
    }
    const errors = [];
    validate(data, SCHEMA, '', errors);
    if (errors.length) {
      problems.push(...errors.map((e) => `${file}: ${e.replace(/^\./, '')}`));
      continue;
    }
    if (cutoff && Date.parse(data.finished_at) < cutoff) continue;
    runs.push(data);
  }
  return { runs, problems };
}

/* ---------------------------------------------------------------- rapportage */

const count = (items, fn) => items.filter(fn).length;
const sum = (items, fn) => items.reduce((total, item) => total + (fn(item) ?? 0), 0);
const pct = (part, whole) => (whole === 0 ? '-' : `${Math.round((part / whole) * 100)}%`);

function tally(items, fn) {
  const map = new Map();
  for (const item of items) {
    const key = fn(item);
    if (key === null || key === undefined) continue;
    map.set(key, (map.get(key) ?? 0) + 1);
  }
  return [...map.entries()].sort((a, b) => b[1] - a[1]);
}

function report(runs, problems) {
  const lines = [];
  const now = new Date().toISOString().slice(0, 10);

  lines.push('# Agent-metrics — rollup');
  lines.push('');
  lines.push('> Gegenereerd door `scripts/metrics-rollup.mjs`. Niet met de hand wijzigen.');
  lines.push(`> Datum: ${now}${days ? ` · venster: laatste ${days} dagen` : ' · venster: alles'}`);
  lines.push('');

  if (runs.length === 0) {
    lines.push('Nog geen runs gemeten. Zodra `metrics-collector` bestanden schrijft in');
    lines.push('`docs/agent-metrics/`, vult dit rapport zichzelf.');
    lines.push('');
    if (problems.length) {
      lines.push('## Bestanden die niet meetellen');
      lines.push('');
      problems.forEach((p) => lines.push(`- ${p}`));
      lines.push('');
    }
    return lines.join('\n');
  }

  const merged = runs.filter((r) => r.outcome === 'merged');
  const gemeten = merged.filter((r) => r.human_correction_commits !== null);
  const schoon = count(gemeten, (r) => r.human_correction_commits === 0);
  const ciGroen = runs.filter((r) => r.ci_first_attempt_green !== null);

  lines.push('## De twee besluitmetrics');
  lines.push('');
  lines.push('| Metric | Waarde | Bepaalt |');
  lines.push('| --- | --- | --- |');
  lines.push(
    `| Runs zonder menselijke correctie | ${pct(schoon, gemeten.length)} (${schoon}/${gemeten.length}) | Of het autonomiedomein mag groeien |`
  );
  const fn = sum(runs, (r) => r.gatekeeper?.false_negatives);
  const fp = sum(runs, (r) => r.gatekeeper?.false_positives);
  lines.push(
    `| Gatekeeper false negatives / positives | ${fn} / ${fp} | Of de reviewagent netto tijd bespaart |`
  );
  lines.push('');
  if (merged.length !== gemeten.length) {
    lines.push(
      `> Let op: ${merged.length - gemeten.length} gemergede run(s) hebben \`human_correction_commits: null\` en tellen niet mee.`
    );
    lines.push('');
  }

  lines.push('## Volume en uitkomst');
  lines.push('');
  lines.push(`- Runs in dit venster: **${runs.length}**`);
  lines.push(`- CI groen bij eerste poging: **${pct(count(ciGroen, (r) => r.ci_first_attempt_green), ciGroen.length)}**`);
  lines.push(`- Geëscaleerd naar een mens: **${pct(count(runs, (r) => r.escalated), runs.length)}**`);
  const duraties = runs.map((r) => r.duration_seconds).filter((d) => typeof d === 'number');
  if (duraties.length) {
    const gemiddeld = Math.round(sum(duraties, (d) => d) / duraties.length / 60);
    lines.push(`- Gemiddelde doorlooptijd: **${gemiddeld} min**`);
  }
  const kosten = sum(runs, (r) => r.tokens?.estimated_cost_eur);
  const tokens = sum(runs, (r) => r.tokens?.total);
  if (tokens) {
    lines.push(`- Tokens totaal: **${tokens.toLocaleString('nl-NL')}** (geschat € ${kosten.toFixed(2)})`);
  }
  lines.push('');

  lines.push('| Uitkomst | Aantal |');
  lines.push('| --- | --- |');
  tally(runs, (r) => r.outcome).forEach(([key, n]) => lines.push(`| ${key} | ${n} |`));
  lines.push('');

  const redenen = tally(runs, (r) => r.escalation_reason);
  if (redenen.length) {
    lines.push('## Escalatieredenen');
    lines.push('');
    lines.push('Wat hier bovenaan staat, is waar `retro-analyst` naar moet kijken.');
    lines.push('');
    lines.push('| Reden | Aantal |');
    lines.push('| --- | --- |');
    redenen.forEach(([key, n]) => lines.push(`| ${key} | ${n} |`));
    lines.push('');
  }

  const metGatekeeper = runs.filter((r) => r.gatekeeper?.advice);
  if (metGatekeeper.length) {
    lines.push('## pr-gatekeeper');
    lines.push('');
    lines.push('| Advies | Aantal |');
    lines.push('| --- | --- |');
    tally(metGatekeeper, (r) => r.gatekeeper.advice).forEach(([key, n]) =>
      lines.push(`| ${key} | ${n} |`)
    );
    lines.push('');
    lines.push(
      `Bevindingen: **${sum(runs, (r) => r.gatekeeper?.findings?.blocker)}** blocker, ` +
        `**${sum(runs, (r) => r.gatekeeper?.findings?.belangrijk)}** belangrijk, ` +
        `**${sum(runs, (r) => r.gatekeeper?.findings?.suggestie)}** suggestie.`
    );
    lines.push('');
  }

  const klassen = tally(runs, (r) => r.ticket_class);
  lines.push('## Per ticketklasse');
  lines.push('');
  lines.push('| Klasse | Runs | Zonder correctie |');
  lines.push('| --- | --- | --- |');
  for (const [klasse, n] of klassen) {
    const g = gemeten.filter((r) => r.ticket_class === klasse);
    lines.push(`| ${klasse} | ${n} | ${pct(count(g, (r) => r.human_correction_commits === 0), g.length)} |`);
  }
  lines.push('');

  lines.push('## Runs');
  lines.push('');
  lines.push('| Datum | Ticket | Klasse | Uitkomst | Retries | Correcties |');
  lines.push('| --- | --- | --- | --- | --- | --- |');
  for (const run of [...runs].sort((a, b) => b.finished_at.localeCompare(a.finished_at))) {
    const pr = run.pr_url ? `[${run.issue_key}](${run.pr_url})` : run.issue_key;
    lines.push(
      `| ${run.finished_at.slice(0, 10)} | ${pr} | ${run.ticket_class} | ${run.outcome} | ` +
        `${run.retries} | ${run.human_correction_commits ?? '—'} |`
    );
  }
  lines.push('');

  if (problems.length) {
    lines.push('## Bestanden die niet meetellen');
    lines.push('');
    lines.push('Deze bestanden voldoen niet aan `schema.json` en zijn overgeslagen.');
    lines.push('');
    problems.forEach((p) => lines.push(`- ${p}`));
    lines.push('');
  }

  return lines.join('\n');
}

/* --------------------------------------------------------------------- main */

const { runs, problems } = loadRuns();

if (validateOnly) {
  if (problems.length) {
    console.error('Ongeldige metrics-bestanden:');
    problems.forEach((p) => console.error(`  - ${p}`));
    process.exit(1);
  }
  console.log(`OK - ${runs.length} metrics-bestand(en) valideren tegen schema.json.`);
  process.exit(0);
}

const target = join(DIR, 'REPORT.md');
writeFileSync(target, `${report(runs, problems)}\n`, 'utf8');
console.log(`Geschreven: docs/agent-metrics/REPORT.md (${runs.length} run(s), ${problems.length} probleem/problemen)`);
