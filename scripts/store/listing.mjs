#!/usr/bin/env node
// Copyright (C) 2026  Gabriel Fontán (BobbyESP)
/**
 * Writes the Google Play listing's text for every language: title, short description and full
 * description, from `store_title`, `store_short_description` and `store_full_description` in each
 * language's `store_listing.xml`.
 *
 * - Every language in `locales.json` that has the three strings is written, whatever its alphabet:
 *   Play shows this text in the system's fonts.
 * - A language without any of them is skipped, and Play keeps what it already has for it.
 * - Play's limits are checked first: 30, 80 and 4000 characters. A language that breaks one, or
 *   has only some of the strings, is not written, and the script exits with 1.
 *
 * Usage:
 *   node listing.mjs                  every language
 *   node listing.mjs --locale es,ja   only these
 *   node listing.mjs --check          check only, write nothing
 *   node listing.mjs --out <dir>
 *
 * The output is fastlane's layout: <out>/<play locale>/title.txt, short_description.txt and
 * full_description.txt, which `fastlane supply` uploads.
 */
import { writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { ROOT, readLocales, readStoreStrings, playLength } from './strings.mjs';

const FIELDS = [
  { name: 'store_title', file: 'title.txt', limit: 30 },
  { name: 'store_short_description', file: 'short_description.txt', limit: 80 },
  { name: 'store_full_description', file: 'full_description.txt', limit: 4000 },
];

function parseArgs(argv) {
  const args = { locales: null, out: path.join(ROOT, 'fastlane/metadata/android'), check: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--locale') args.locales = argv[++i].split(',').map(s => s.trim()).filter(Boolean);
    else if (a === '--out') args.out = path.resolve(argv[++i]);
    else if (a === '--check') args.check = true;
    else throw new Error(`Unknown argument: ${a}`);
  }
  return args;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const all = await readLocales();
  const chosen = args.locales ? all.filter(([l]) => args.locales.includes(l)) : all;
  const unknown = (args.locales ?? []).filter(l => !all.some(([k]) => k === l));
  if (unknown.length) throw new Error(`Not in locales.json: ${unknown.join(', ')}`);

  const problems = [];
  for (const [language, { play }] of chosen) {
    const source = await readStoreStrings(language);
    const values = FIELDS.map(f => source?.strings[f.name]);
    if (values.every(v => v === undefined)) {
      if (language === 'en') problems.push('en: the default language has no listing strings');
      else console.log(`${language}: no listing strings, skipped (Play keeps what it has).`);
      continue;
    }

    const issues = [];
    FIELDS.forEach((f, i) => {
      const v = values[i];
      if (v === undefined || v.trim() === '') issues.push(`missing ${f.name}`);
      else if (playLength(v) > f.limit) issues.push(`${f.name} is ${playLength(v)} characters, over ${f.limit}`);
    });
    if (issues.length) { problems.push(...issues.map(m => `${language}: ${m}`)); continue; }

    const sizes = FIELDS.map((f, i) => `${playLength(values[i])}/${f.limit}`).join(', ');
    if (args.check) { console.log(`${language} ok (${sizes})`); continue; }
    const dir = path.join(args.out, play);
    await mkdir(dir, { recursive: true });
    for (let i = 0; i < FIELDS.length; i++) await writeFile(path.join(dir, FIELDS[i].file), values[i]);
    console.log(`${language} → ${path.relative(ROOT, dir)} (${sizes})`);
  }

  if (problems.length) {
    console.error('\nNot written:\n  ' + problems.join('\n  '));
    process.exit(1);
  }
}

main().catch(error => { console.error(error.message ?? error); process.exit(1); });
