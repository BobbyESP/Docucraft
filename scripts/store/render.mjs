#!/usr/bin/env node
// Copyright (C) 2026  Gabriel Fontán (BobbyESP)
/**
 * Draws the Google Play graphics for each language: the eight phone screenshots, as one continuous
 * panorama cut into 1080 × 1920 frames, and the 1024 × 500 feature graphic.
 *
 * - The copy comes from the app's own resources, `store_listing.xml` in each `values-*` folder, so
 *   it is translated with the rest of the app.
 * - The layout comes from `layout.json`: the same numbers as the Figma file and the design document.
 * - The app's screens come from `captures/<language>/<frame>.png`. A frame without its own capture
 *   uses the English one, and without that a labelled placeholder.
 *
 * Only the languages marked `graphics` in `locales.json` are drawn: the brand's fonts cover the
 * Latin alphabet only. Google Play shows the default language's screenshots to every other language.
 *
 * Usage:
 *   node render.mjs                       every language in layout.json
 *   node render.mjs --locale es,fr        only these
 *   node render.mjs --preview             also write each panorama and feature graphic to build/store-screenshots
 *   node render.mjs --captures <dir> --out <dir>
 *
 * The output is fastlane's layout: <out>/<play locale>/images/phoneScreenshots/01.png … 08.png and
 * <out>/<play locale>/images/featureGraphic.png.
 * Exits with 1 if any copy does not fit, so a long translation cannot reach the store unnoticed.
 */
import { readFile, writeFile, mkdir, readdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { chromium } from 'playwright';
import { HERE, ROOT, RES, exists, readLocales, readStoreStrings } from './strings.mjs';

const FONTS = path.join(RES, 'font');

function parseArgs(argv) {
  const args = { locales: null, captures: path.join(HERE, 'captures'), out: path.join(ROOT, 'fastlane/metadata/android'), preview: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--locale') args.locales = argv[++i].split(',').map(s => s.trim()).filter(Boolean);
    else if (a === '--captures') args.captures = path.resolve(argv[++i]);
    else if (a === '--out') args.out = path.resolve(argv[++i]);
    else if (a === '--preview') args.preview = true;
    else throw new Error(`Unknown argument: ${a}`);
  }
  return args;
}

/** The graphics' copy of one language, by frame id and 'feature', or null if it has no store_listing.xml. */
async function readCopy(language, frameIds) {
  const source = await readStoreStrings(language);
  if (!source) return null;
  const name = id => id === 'feature' ? 'store_feature' : `store_screenshot_${id}`;
  const copy = {}, missing = [];
  for (const id of [...frameIds, 'feature']) {
    copy[id] = {};
    for (const key of ['headline', 'supporting']) {
      const text = source.strings[`${name(id)}_${key}`];
      if (text === undefined) missing.push(`${name(id)}_${key}`);
      else copy[id][key] = text.replace(/\s+/g, ' ');
    }
  }
  return { file: source.file, copy, missing };
}

async function dataUrl(file, type) {
  return `data:${type};base64,${(await readFile(file)).toString('base64')}`;
}

async function captureFor(captures, locale, id) {
  for (const l of [locale, 'en']) {
    const file = path.join(captures, l, `${id}.png`);
    if (await exists(file)) return { url: await dataUrl(file, 'image/png'), from: l };
  }
  return { url: null, from: null };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const layout = JSON.parse(await readFile(path.join(HERE, 'layout.json'), 'utf8'));
  const playLocales = Object.fromEntries((await readLocales()).filter(([, l]) => l.graphics).map(([k, l]) => [k, l.play]));
  const locales = args.locales ?? Object.keys(playLocales);
  const unknown = locales.filter(l => !playLocales[l]);
  if (unknown.length) throw new Error(`No graphics for: ${unknown.join(', ')}. Only languages marked "graphics" in locales.json are drawn.`);

  const fonts = {
    headline: await dataUrl(path.join(FONTS, layout.copy.headline.font), 'font/ttf'),
    supporting: await dataUrl(path.join(FONTS, layout.copy.supporting.font), 'font/ttf'),
    label: await dataUrl(path.join(FONTS, layout.copy.labelFont), 'font/ttf'),
  };
  const F = layout.canvas.frameWidth, H = layout.canvas.height, W = F * layout.canvas.frameCount;
  const ids = layout.frames.map(f => f.id);

  const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {});
  const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
  const problems = [];

  try {
    for (const locale of locales) {
      const playLocale = playLocales[locale];
      const source = await readCopy(locale, ids);
      if (!source) { console.log(`${locale}: no store_listing.xml, skipped (Play will show the default language).`); continue; }
      if (source.missing.length) { problems.push(`${locale}: missing ${source.missing.join(', ')} in ${path.relative(ROOT, source.file)}`); continue; }

      const frames = [], fallbacks = [];
      for (const id of ids) {
        const capture = await captureFor(args.captures, locale, id);
        if (capture.from !== locale) fallbacks.push(`${id}${capture.from ? ' (en)' : ' (placeholder)'}`);
        frames.push({ ...source.copy[id], capture: capture.url });
      }

      await page.goto(pathToFileURL(path.join(HERE, 'template.html')).href);
      const report = await page.evaluate(data => drawPanorama(data), { layout, fonts, frames });

      const shots = [];
      for (let i = 0; i < ids.length; i++) shots.push(await page.screenshot({ clip: { x: i * F, y: 0, width: F, height: H } }));
      const panorama = args.preview ? await page.screenshot({ clip: { x: 0, y: 0, width: W, height: H } }) : null;

      const G = layout.featureGraphic, featureCaptures = {};
      for (const p of G.phones) featureCaptures[p.frame] = frames[ids.indexOf(p.frame)].capture;
      await page.goto(pathToFileURL(path.join(HERE, 'template.html')).href);
      const featureReport = await page.evaluate(data => drawFeatureGraphic(data), { layout, fonts, copy: source.copy.feature, captures: featureCaptures });
      const feature = await page.screenshot({ clip: { x: 0, y: 0, width: G.width, height: G.height } });

      const overflow = [...report, featureReport].flatMap(r => ['headline', 'supporting'].filter(k => !r[k].fits)
        .map(k => `${locale} ${r.id} ${k}: ${r[k].lines} lines even at ${r[k].size} px`));
      if (overflow.length) { problems.push(...overflow); continue; }

      const images = path.join(args.out, playLocale, 'images');
      const dir = path.join(images, 'phoneScreenshots');
      await mkdir(dir, { recursive: true });
      for (const name of await readdir(dir)) if (name.endsWith('.png')) await rm(path.join(dir, name));
      for (let i = 0; i < ids.length; i++) await writeFile(path.join(dir, `${ids[i]}.png`), shots[i]);
      await writeFile(path.join(images, 'featureGraphic.png'), feature);
      if (panorama) {
        const previewDir = path.join(ROOT, 'build', 'store-screenshots');
        await mkdir(previewDir, { recursive: true });
        await writeFile(path.join(previewDir, `${locale}.png`), panorama);
        await writeFile(path.join(previewDir, `${locale}-feature.png`), feature);
      }

      const specFor = id => (id === 'feature' ? G.copy : layout.copy);
      const shrunk = [...report, featureReport].flatMap(r => ['headline', 'supporting'].filter(k => r[k].size < specFor(r.id)[k].size).map(k => `${r.id} ${k} ${r[k].size}px`));
      console.log(`${locale} → ${path.relative(ROOT, dir)}` +
        (shrunk.length ? `\n  shrunk to fit: ${shrunk.join(', ')}` : '') +
        (fallbacks.length ? `\n  no capture of its own: ${fallbacks.join(', ')}` : ''));
    }
  } finally {
    await browser.close();
  }

  if (problems.length) {
    console.error('\nNot drawn:\n  ' + problems.join('\n  '));
    process.exit(1);
  }
}

main().catch(error => { console.error(error.message ?? error); process.exit(1); });
