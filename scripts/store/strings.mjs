// Copyright (C) 2026  Gabriel Fontán (BobbyESP)
/**
 * Reads the store's copy from the app's resources: `store_listing.xml` in `values` (English) and in
 * each `values-<language>` folder. Shared by listing.mjs and render.mjs.
 */
import { readFile, access } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const HERE = path.dirname(fileURLToPath(import.meta.url));
export const ROOT = path.resolve(HERE, '../..');
export const RES = path.join(ROOT, 'app/src/main/res');

export const exists = p => access(p).then(() => true, () => false);

/** The languages in locales.json, as [language, { play, graphics }]. */
export async function readLocales() {
  return Object.entries(JSON.parse(await readFile(path.join(HERE, 'locales.json'), 'utf8')));
}

function decodeEntities(s) {
  return s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16)))
    .replace(/&amp;/g, '&');
}

/**
 * A string resource's text as Android reads it: outside double quotes, runs of whitespace become
 * one space and the ends are trimmed; `\n`, `\t`, `\uXXXX` and `\'`-style escapes are resolved.
 */
export function androidText(raw) {
  const s = decodeEntities(raw);
  let out = '', quoted = false, pendingSpace = false;
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (c === '\\' && i + 1 < s.length) {
      const n = s[++i];
      if (pendingSpace) { out += ' '; pendingSpace = false; }
      if (n === 'n') out += '\n';
      else if (n === 't') out += '\t';
      else if (n === 'u') { out += String.fromCharCode(parseInt(s.slice(i + 1, i + 5), 16)); i += 4; }
      else out += n;
    } else if (c === '"') {
      quoted = !quoted;
    } else if (!quoted && /\s/.test(c)) {
      pendingSpace = out.length > 0;
    } else {
      if (pendingSpace) { out += ' '; pendingSpace = false; }
      out += c;
    }
  }
  return out;
}

/** The `store_*` strings of one language, by name, or null if it has no store_listing.xml. */
export async function readStoreStrings(language) {
  const folder = language === 'en' ? 'values' : `values-${language}`;
  const file = path.join(RES, folder, 'store_listing.xml');
  if (!(await exists(file))) return null;
  const xml = (await readFile(file, 'utf8')).replace(/<!--[\s\S]*?-->/g, '');
  const strings = {};
  for (const m of xml.matchAll(/<string\s+name="(store_[a-z0-9_]+)"[^>]*>([\s\S]*?)<\/string>/g)) {
    strings[m[1]] = androidText(m[2]);
  }
  return { file, strings };
}

/** Length as Google Play counts it: in characters, not UTF-16 units. */
export const playLength = s => [...s].length;
