<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Testing

## What is tested where

| Module | JVM (`src/test`) | On a device (`src/androidTest`) |
|---|---|---|
| `:app` | use cases, ViewModels, the D2 settings table, link resolution, selection decisions, navigation rules, text splitting, layered content | the platform content provider against the fixtures |
| `:composepdf` | layout geometry, tile planning, zoom steps, viewport, load-error classification | fixtures, restoration, layout changes, content padding, gestures, extension points, scroll-to, load errors, platform content APIs |
| `:document-content-api` | selection: carets, runs, layouts, right-to-left, across pages | — |
| `:scanner-mlkit` | the scan result decision table, the activity-result host and process death | — |

**The rule of thumb:**
- **Decisions are pure and go on the JVM**: what a touch selects, what a link does, what a result
  means.
- **Device tests check what only the platform can answer**: what `PdfRenderer` reports, how Compose
  delivers gestures, how the engine draws.
- **A port is tested with a fake**, never with a mock of the framework.

## Conventions

- **A test sits in the package of the class it tests**, so it reaches `internal` code and is found
  next to it.
- **Doubles and fixtures shared by several tests** live in one place instead of being copied:
  - `:app`: `FakeDocumentStorage` (docscanner), `FakePageContentProvider` and `textPage` (pdfviewer);
  - `:composepdf` JVM: `layoutOf` and `threePages` build a `PageLayoutSnapshot` the way the real
    layout does;
  - `:composepdf` on a device: `LongDocument` and `waitUntilLoaded` (`ViewerTestSupport.kt`), and the
    gesture timings (`GestureTimings.kt`).
- **Names say the behaviour**: backtick sentences on the JVM; camelCase in device tests, whose
  names cannot hold spaces, and throughout `:composepdf`, most of whose tests are device tests.
- **Comments explain why a case matters**, in present tense. The history of a bug belongs in git.

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
