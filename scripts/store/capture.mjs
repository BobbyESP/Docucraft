#!/usr/bin/env node
// Copyright (C) 2026  Gabriel Fontán (BobbyESP)
/**
 * Takes the app's screens for the store's screenshots: the eight that render.mjs puts inside the
 * phones of the panorama, in each language that has graphics.
 *
 * The screens are the app's own, on an emulator: `StoreCaptureTest` (in `:app`'s device tests)
 * shows each one over a sample library, in the brand's colors and under a clean status bar, and
 * photographs it. This script finds or starts the emulator, runs that test through Gradle, and
 * copies what it took to `captures/<language>/01.png … 08.png`, where render.mjs looks.
 *
 * Usage:
 *   node capture.mjs                      every language marked "graphics" in locales.json
 *   node capture.mjs --locale es,fr       only these
 *   node capture.mjs --avd Pixel_10_Pro_XL   start this emulator if none is running, and stop it after
 *   node capture.mjs --serial emulator-5554  use this device
 *   node capture.mjs --out <dir>
 *
 * Without --serial (or ANDROID_SERIAL) the one running emulator is used. A phone is only used when
 * named: the test installs the debug build and changes the device's status bar while it runs.
 * Without --avd (or STORE_AVD) nothing is started, and no emulator running is an error.
 * Exits with 1 if any frame of any language is missing.
 */
import { readFile, mkdir, readdir, rm, copyFile } from 'node:fs/promises';
import { spawn, spawnSync } from 'node:child_process';
import path from 'node:path';
import { HERE, ROOT, exists, readLocales } from './strings.mjs';

const TEST = 'com.bobbyesp.docucraft.store.StoreCaptureTest';
const FRAMES = ['01', '02', '03', '04', '05', '06', '07', '08'];
/** Where the Android Gradle plugin leaves what a device test wrote for it to collect. */
const COLLECTED = path.join(ROOT, 'app/build/outputs/connected_android_test_additional_output');
const BOOT_TIMEOUT_MS = 5 * 60 * 1000;
const WINDOWS = process.platform === 'win32';

function parseArgs(argv) {
  const args = {
    locales: null,
    out: path.join(HERE, 'captures'),
    serial: process.env.ANDROID_SERIAL || null,
    avd: process.env.STORE_AVD || null,
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--locale') args.locales = argv[++i].split(',').map(s => s.trim()).filter(Boolean);
    else if (a === '--out') args.out = path.resolve(argv[++i]);
    else if (a === '--serial') args.serial = argv[++i];
    else if (a === '--avd') args.avd = argv[++i];
    else throw new Error(`Unknown argument: ${a}`);
  }
  return args;
}

/** The Android SDK: where the environment says, or where Gradle is told in local.properties. */
async function sdkDir() {
  const fromEnv = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
  if (fromEnv) return fromEnv;
  const file = path.join(ROOT, 'local.properties');
  if (await exists(file)) {
    const line = (await readFile(file, 'utf8')).split(/\r?\n/).find(l => l.startsWith('sdk.dir='));
    // A properties file escapes backslashes and colons.
    if (line) return line.slice('sdk.dir='.length).replace(/\\(.)/g, '$1');
  }
  throw new Error('No Android SDK: set ANDROID_HOME, or sdk.dir in local.properties.');
}

function run(file, args, options = {}) {
  const result = spawnSync(file, args, { encoding: 'utf8', ...options });
  if (result.error) throw result.error;
  return result;
}

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

/** The serials of the devices adb can reach and use. */
function devices(adb) {
  return run(adb, ['devices']).stdout.split(/\r?\n/).slice(1)
    .map(line => line.trim().split(/\s+/))
    .filter(([serial, state]) => serial && state === 'device')
    .map(([serial]) => serial);
}

const isEmulator = serial => serial.startsWith('emulator-');

/**
 * Starts the emulator [avd] without a window and waits until it has booted. Read-only, so that
 * nothing the screenshots do to it (the app in another language, the status bar's demo mode, the
 * debug build) is still there the next time it is opened.
 */
async function startEmulator(sdk, adb, avd) {
  const before = new Set(devices(adb));
  const emulator = path.join(sdk, 'emulator', WINDOWS ? 'emulator.exe' : 'emulator');
  console.log(`Starting the emulator ${avd}…`);
  const child = spawn(emulator, ['-avd', avd, '-read-only', '-no-snapshot-save', '-no-window', '-no-audio', '-no-boot-anim'],
    { detached: true, stdio: 'ignore' });
  child.unref();
  let failed = null;
  child.on('error', error => { failed = error; });
  child.on('exit', code => { if (code) failed = new Error(`The emulator ${avd} exited with ${code}.`); });

  const deadline = Date.now() + BOOT_TIMEOUT_MS;
  while (Date.now() < deadline) {
    if (failed) throw failed;
    const serial = devices(adb).find(s => isEmulator(s) && !before.has(s));
    if (serial && run(adb, ['-s', serial, 'shell', 'getprop', 'sys.boot_completed']).stdout.trim() === '1') return serial;
    await sleep(2000);
  }
  throw new Error(`The emulator ${avd} did not boot in ${BOOT_TIMEOUT_MS / 60000} minutes.`);
}

/** The device to use, and whether this script started it and so has to stop it. */
async function chooseDevice(sdk, adb, args) {
  const attached = devices(adb);
  if (args.serial) {
    if (!attached.includes(args.serial)) throw new Error(`No device ${args.serial}. Attached: ${attached.join(', ') || 'none'}.`);
    return { serial: args.serial, started: false };
  }
  const emulators = attached.filter(isEmulator);
  if (emulators.length === 1) return { serial: emulators[0], started: false };
  if (emulators.length > 1) throw new Error(`Several emulators are running (${emulators.join(', ')}): pick one with --serial.`);
  if (args.avd) return { serial: await startEmulator(sdk, adb, args.avd), started: true };
  throw new Error('No emulator is running. Start one, or name one to start with --avd <name> (or STORE_AVD).' +
    (attached.length ? ` To use the phone that is attached, name it: --serial ${attached[0]}.` : ''));
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const graphics = (await readLocales()).filter(([, l]) => l.graphics).map(([language]) => language);
  const locales = args.locales ?? graphics;
  const unknown = locales.filter(l => !graphics.includes(l));
  if (unknown.length) throw new Error(`No graphics for: ${unknown.join(', ')}. Only languages marked "graphics" in locales.json are captured.`);

  const sdk = await sdkDir();
  const adb = path.join(sdk, 'platform-tools', WINDOWS ? 'adb.exe' : 'adb');
  const device = await chooseDevice(sdk, adb, args);
  console.log(`Capturing ${locales.join(', ')} on ${device.serial}`);

  try {
    // What an earlier run collected would pass for this one's.
    await rm(COLLECTED, { recursive: true, force: true });
    // On Windows the wrapper is a batch file, which only the command interpreter can run.
    const wrapper = WINDOWS ? ['cmd.exe', '/d', '/c', path.join(ROOT, 'gradlew.bat')] : [path.join(ROOT, 'gradlew')];
    const gradle = run(wrapper[0], [
      ...wrapper.slice(1),
      ':app:connectedDebugAndroidTest',
      `-Pandroid.testInstrumentationRunnerArguments.class=${TEST}`,
      '-Pandroid.testInstrumentationRunnerArguments.storeCaptures=true',
      // Joined by a plus sign: a comma does not survive the way to the device.
      `-Pandroid.testInstrumentationRunnerArguments.storeLocales=${locales.join('+')}`,
      // The task uninstalls the app when it is done, and with it the library of whoever uses
      // the debug build on this device.
      '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true',
    ], { cwd: ROOT, stdio: 'inherit', env: { ...process.env, ANDROID_SERIAL: device.serial } });

    const missing = await collect(locales, args.out);
    if (gradle.status !== 0) throw new Error('The capture run failed: see the report Gradle names above.');
    if (missing.length) throw new Error(`Not captured: ${missing.join(', ')}.`);
    console.log(`${locales.length * FRAMES.length} screens → ${path.relative(ROOT, args.out)}`);
  } finally {
    if (device.started) run(adb, ['-s', device.serial, 'emu', 'kill']);
  }
}

/** Copies what the run took into `<out>/<language>/`, and returns the frames it did not take. */
async function collect(locales, out) {
  // One folder for each device the task ran on; it only ever runs on the one chosen here.
  const base = path.join(COLLECTED, 'debugAndroidTest', 'connected');
  const folders = (await exists(base)) ? await readdir(base) : [];
  const missing = [];
  for (const locale of locales) {
    const target = path.join(out, locale);
    for (const frame of FRAMES) {
      let source = null;
      for (const folder of folders) {
        const candidate = path.join(base, folder, 'store-captures', locale, `${frame}.png`);
        if (await exists(candidate)) source = candidate;
      }
      if (!source) { missing.push(`${locale} ${frame}`); continue; }
      await mkdir(target, { recursive: true });
      await copyFile(source, path.join(target, `${frame}.png`));
    }
  }
  return missing;
}

main().catch(error => { console.error(error.message ?? error); process.exit(1); });
