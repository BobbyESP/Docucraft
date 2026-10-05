<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Testing

## What is tested where

| Module | JVM (`src/test`) | On a device (`src/androidTest`) |
|---|---|---|
| `:app` | use cases, ViewModels, the D2 settings table, link resolution, selection decisions, navigation rules, text splitting, layered content, the path a migration gives an old document | the platform content provider against the fixtures; the catalogue: migrations from every exported schema, a migrated schema against a new one, the triggers, search |
| `:composepdf` | layout geometry, tile planning, zoom steps, viewport, load-error classification | fixtures, restoration, layout changes, content padding, gestures, extension points, scroll-to, load errors, platform content APIs |
| `:document-content-api` | selection: carets, runs, layouts, right-to-left, across pages | — |
| `:scanner-mlkit` | the scan result decision table, the activity-result host and process death | — |

**The rule of thumb:**
- **Decisions are pure and go on the JVM**: what a touch selects, what a link does, what a result
  means.
- **Device tests check what only the platform can answer**: what `PdfRenderer` reports, how Compose
  delivers gestures, how the engine draws, what the device's SQLite does with a query, a trigger
  or a migration.
- **A port is tested with a fake**, never with a mock of the framework.

## Conventions

- **A test sits in the package of the class it tests**, so it reaches `internal` code and is found
  next to it.
- **Doubles and fixtures shared by several tests** live in one place instead of being copied:
  - `:app`: `FakeDocumentStorage`, `FakeDocumentsRepository`, `FakeDocumentThumbnails`,
    `FakeSearchIndex` and `testDocument` (docscanner), `FakePageContentProvider` and `textPage`
    (pdfviewer);
  - `:app` on a device: `MigrationTestSupport.kt` builds an old version of the catalogue from its
    exported schema and fills it with rows as that version stored them;
  - `:composepdf` JVM: `layoutOf` and `threePages` build a `PageLayoutSnapshot` the way the real
    layout does;
  - `:composepdf` on a device: `LongDocument` and `waitUntilLoaded` (`ViewerTestSupport.kt`), and the
    gesture timings (`GestureTimings.kt`).
- **Names say the behaviour**: backtick sentences on the JVM; camelCase in device tests, whose
  names cannot hold spaces, and throughout `:composepdf`, most of whose tests are device tests.
- **Comments explain why a case matters**, in present tense. The history of a bug belongs in git.

## The one device test that tests nothing

`StoreCaptureTest` (`:app`, package `store`) takes the app's screens for the Google Play
screenshots. It is a device test because that is what can put the app's screens on a device, over
a sample library and in the brand's colors. It is skipped unless `storeCaptures=true` is passed,
which only `scripts/store/capture.mjs` does, so `connectedDebugAndroidTest` neither takes
screenshots nor touches the status bar. What it does and why: [`scripts/store/README.md`](../scripts/store/README.md#the-apps-screens).

It starts the app's graph anew (`appModules`, with the catalogue and the settings replaced), which
is why it must not share a run with the tests that read the graph the app started.

## Running

```sh
./gradlew testDebugUnitTest :scanner-api:test :document-content-api:test      # every JVM test
./gradlew :app:connectedDebugAndroidTest :composepdf:connectedDebugAndroidTest
./gradlew :composepdf:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.composepdf.gesture.PdfGesturesTest
```

- **Device tests run on every attached device**, and so does `installDebug`. A phone paired over
  wireless ADB counts, even if nothing shows it. Target one with `ANDROID_SERIAL`, for example
  `ANDROID_SERIAL=emulator-5554`, and prefer the emulator unless a real device is really needed.
- **The platform's text APIs need API 35+.** Tests that use them carry
  `@SdkSuppress(minSdkVersion = 35)`.
- **The catalogue's tests also run on API 24**, the oldest SQLite the app meets (3.9), where SQL
  that works everywhere else can fail. The AVD used for it is a Google APIs image of API 24.
- **So do the tests that open PDFs.** Android 7's renderer crashes the process, and with it the
  whole run, after a document that fails to open or when two are drawn at once
  ([pdf-engine.md](pdf-engine.md#android-7)). A test opens its renderers through `PdfRenderers`,
  like the app, or the first protected fixture takes down every test after it.
- **A run can pass without running anything.** If a copy of the app is already installed, on API 24
  the task's own install fails, no test runs, and the build still succeeds. Uninstall
  `com.bobbyesp.docucraft.debug` and `com.bobbyesp.docucraft.debug.test` first, and read the number
  of tests in `app/build/outputs/androidTest-results/connected/debug/TEST-*.xml` rather than the
  build result.
- **Reading an app file after a test**: pass
  `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`, then use
  `adb shell run-as <package> …`.

## Fixtures

The viewer's test PDFs and their `manifest.json` (the expected position of every word and link)
are in `composepdf/src/androidTest/assets/fixtures/`. What each one covers, and how to regenerate
them: [`testing/pdf-fixtures/README.md`](../testing/pdf-fixtures/README.md).

**Never commit a document with personal data** as a fixture. Build an equivalent with invented
content.

## Checking by hand on the emulator

Some behaviour only shows on a screen: selecting, handles, auto-scroll, link previews, Custom Tabs.
The recipe used so far:

```sh
adb -s emulator-5554 push composepdf/src/androidTest/assets/fixtures/text-and-links.pdf /sdcard/Download/
adb -s emulator-5554 shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/text-and-links.pdf
adb -s emulator-5554 shell content query --uri content://media/external/downloads --projection _id:_display_name
adb -s emulator-5554 shell am start -a android.intent.action.VIEW -t application/pdf \
    -d content://media/external/downloads/<id> --grant-read-uri-permission \
    -n com.bobbyesp.docucraft.debug/com.bobbyesp.docucraft.feature.pdfviewer.presentation.PdfViewerActivity
```

- **Gestures.** Drive them with `input swipe` for a long press, and with
  `input motionevent DOWN/MOVE/UP` for a drag that holds. Take screenshots with
  `exec-out screencap -p`.
- **Finding things on screen.** Prefer the accessibility dump (`uiautomator dump`) to fixed
  coordinates: the viewer restores its scroll position, and coordinates drift. Each link on the
  page appears there as "Link: …".
- **Checking what was copied.** Paste it into a text field, such as Settings' search, and read the
  field back from the dump.
- **The system text toolbar is not in the dump.** Take a screenshot to find it.
- **After a reboot, the emulator's downloads may be empty.** Push the fixtures again.
