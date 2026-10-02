# AGENTS.md

Docucraft is a local-first Android app: it scans documents to PDF with ML Kit, keeps them in a
local catalogue, and reads PDFs — its own and any other app's — in an in-house viewer with text
selection and safe links. Nothing leaves the device unless the user shares it.

This file is the map and the rules. How each subsystem works, and why, is in
[`docs/`](docs/README.md): read the relevant document before changing a subsystem.

---

## 1. Modules

| Module | What it is | Rule |
|---|---|---|
| `:app` | The product: storage, UI, navigation, widget, analytics. | Depends on everything below; nothing depends on it. |
| `:composepdf` | The PDF engine: rendering, layout, gestures. Public API in `com.composepdf`, internals in `com.composepdf.internal`. | Generic. It knows pages, pixels and fingers, **never** text or links. |
| `:scanner-api` | The scanning contract (`DocumentScanner`, `ScanRequest`, `ScanOutcome`, `ContentRef`…). | Plain Kotlin, zero dependencies. |
| `:scanner-mlkit` | ML Kit's implementation of that contract. | ML Kit is an `implementation` dependency, so no ML Kit type ever reaches `:app`'s classpath. |
| `:document-content-api` | What is on a document's pages (words with their boxes, links) and the pure text-selection logic. | Plain Kotlin. A future OCR module implements it, as `:scanner-mlkit` implements `:scanner-api`. |

Build setup:
- SDKs and JVM target: `buildSrc/src/main/kotlin/ProjectConfig.kt`. Currently minSdk 24, compile
  37.1 (Compose 1.13 requires it), target 37, Java 17.
- `:app` and `:composepdf` apply `docucraft.android.convention` (`buildSrc`: Compose, SDKs,
  desugaring). `:scanner-mlkit` has no UI, so it skips it, but reads the same `ProjectConfig`.
- Library versions: `gradle/libs.versions.toml`. The app's version: root `build.gradle.kts`.

## 2. Where things live in `:app`

```
App.kt                     composition root: startKoin with every module
MainActivity.kt            Home and everything reached from it; lends its result launcher to the scanner
core/                      shared by features
  data/                    DataStore preferences, Firebase analytics
  domain/                  SettingsRepository, StringProvider, notifications, shared models
  presentation/            navigation shell, theme, settings screens, common components
  util/                    BaseViewModel, UiEvent, date/time
  di/                      commonModule, preferencesModule, notificationsServiceModule, analyticsModule
feature/docscanner/        scanning, the catalogue (Room), Home, document actions, the widget
feature/pdfviewer/         the viewer: settings, details, text selection, links, the external-PDF activity
feature/shared/            what both features need (BasicDocument)
```

Each feature has the same shape:
- `domain/`: models, ports, use cases;
- `data/`: implementations of the ports;
- `presentation/`: ViewModels, screens, components;
- `di/`: its Koin modules;
- `navigation/`: its keys.

A new Koin module is registered in `App.kt`.

---

## 3. Rules

### Layers

- **Feature domains are plain Kotlin.** `feature/*/domain` imports no Android, no Compose and no
  data layer. Locations are `ContentRef` or URI strings, never `android.net.Uri`. Two known
  exceptions, in `core/domain`: `UserPreferences` and `InAppNotification` carry Compose types
  (stabilization phase 4, pending).
- **Business logic is a use case** in `feature/<feature>/domain/usecase`, registered in that
  feature's DI module. For the scanner that is `ScannedDocumentModule.kt`; for the viewer,
  `PdfViewerModule.kt`.
- **Framework work goes behind a port.** The interface lives in the domain and the implementation
  in data. Examples: `DocumentStorage`, `DocumentThumbnails`, `SearchIndex`, `DocumentSharer`,
  `DocumentOpener`, `DocumentPrinter`, `LinkOpener`, `PageContentProvider`. A port that needs an
  `Activity` is a Koin `factory` taking it through `parametersOf(activity)`. It is called by the
  screen, in response to an effect from the ViewModel.
- **What the user can cause is a result, not an exception**: cancelling, a page without text, a
  refused link. Examples: `ScanOutcome`, `ExportOutcome`, `PageContentResult`, `LinkAction`.
- **Test a port with a fake**, not with a mock of the framework.

### ViewModels

- Screens use MVI on `core/util/viewModel/BaseViewModel<Intent, State, Effect>`:
  - `state` is a `StateFlow`;
  - `effects` are commands for whoever is on screen *now*, and are **dropped** if nobody listens;
  - `defaultEvents` are messages for the user, and are **buffered** until shown.
- A ViewModel never holds a `Context`: text comes from `StringProvider`.
- Pure UI state stays in the composition, not in the ViewModel. Examples: scroll, zoom, and
  whether the viewer's bars are showing. The viewer's `PdfViewerState` plays the same role as a
  `LazyListState`.
- What must survive process death goes in the `SavedStateHandle`. Examples: `scan_in_flight` in
  `HomeViewModel`, and the viewer's display settings.

### Navigation

- **One back stack, one Navigation 3 `NavDisplay`**: `core/presentation/navigation/DocucraftApp.kt`,
  rendered by `DocucraftNavDisplay.kt`. `PdfViewerActivity` reuses that display with its own stack.
- **Keys are typed and `@Serializable`, and each feature owns its own.**
  - Scanner: `feature/docscanner/navigation/HomeKey.kt`, `DocumentSearchKey.kt` and
    `DocumentActionKeys.kt`.
  - Viewer: `feature/pdfviewer/navigation/PdfViewerKey.kt`.
  - Settings: `core/presentation/screens/preferences/navigation/SettingsKeys.kt`.
- **Features never touch the stack.** They get a `Navigator`
  (`core/presentation/navigation/Navigator.kt`: `goTo`, `goBack`, `goBackWhile`,
  `removeDestination`).
- **A sheet or dialog the user can reach, leave and come back to is a destination on that same
  stack.** Give it a key, and let `OverlaySceneStrategy` (`core/presentation/navigation/overlay/`)
  choose its container. **Never put a `NavDisplay` inside a sheet or dialog.** A nested display is
  only for a self-contained flow that is discarded whole and survives nothing. This is the decision
  that caused the most bugs in this codebase; see
  [docs/navigation.md](docs/navigation.md#modal-destinations).
- **Transient popups anchored to content are not destinations.** A dropdown menu, or a link's
  preview, moves with the page and is closed by any scroll: it is UI state.
- **A destination is told about its surroundings; it never measures them.**
  - `LocalPaneContext` says whether it shares the window.
  - `LocalOverlayContext` says which container it landed in, and whether there is room to stack.
  - Reading `currentWindowAdaptiveInfo` or the device orientation from a screen is a bug.
- **Transitions live in one file**: `core/presentation/navigation/motion/NavigationMotion.kt`.
  Screens contribute nothing to them. A destination reached through a shared element uses
  `SharedElementMotion` from that file, and marks the element with
  `Modifier.sharedBoundsAcrossDestinations`.
- **App-wide services reach the UI as composition locals**, from
  `core/presentation/common/CompositionLocals.kt`: `LocalDarkTheme`, `LocalSettingsRepository`,
  `LocalNotificationsService` and `LocalAnalyticsHelper`.

### Theme

- **A theme change is one crossfade at the theme**, not an animated color scheme
  (`core/presentation/theme/ThemeTransition.kt`). Never pass `animate = true` to MaterialKolor or
  animate `ColorScheme`: every new scheme recomposes the whole tree.
- **Components animate their own state, never theme colors.** Animate a fraction (pressed, selected,
  enabled, scrolled) and `lerp` between colors read from `MaterialTheme`; do not
  `animateColorAsState` to a theme color. See [docs/architecture.md](docs/architecture.md#theme).
- **A surface floating over moving content is frosted, not shadowed**: `Modifier.frosted` with
  `DocucraftBlurDefaults.surfaceStyle(role)`, over content recorded with `Modifier.hazeSource`. The
  source is never an ancestor of what frosts it. An element floating over content is lifted by
  `Modifier.blurHalo` instead of a shadow (a menu: `HaloDropdownMenuPopup`), keeping the shadow
  where the halo is not supported. Content taken out of focus uses `Modifier.blur` with a
  `BlurRadiusSpec`. See [docs/architecture.md](docs/architecture.md#blur).
- Color schemes are generated only when their inputs change (`rememberColorScheme` in `Theme.kt`).
  Every change is built off the main thread. The one exception is the first scheme, built in
  composition because the first frame needs it.

### Scanner

- **The engine is swapped at one line**: the `DocumentScanner` binding in
  `feature/docscanner/di/DocumentScannerModule.kt`.
- ML Kit options are derived from a `ScanRequest` inside `MlKitDocumentScanner`.
- `:app` sees only the contract and `ActivityResultHostImpl`, the host `MainActivity` lends its
  launcher to.

### PDF viewer and engine

- **`:composepdf` stays generic.** Features plug into it through two hooks, and never through
  internals:
  - **`PdfInteractionHandler`**: claim a long press (then receive the drag) or a tap (delivered at
    once, without the double-tap wait).
  - **`PdfOverlayScope`**: draw on pages (`DrawOnPages`), place something at a point on a page
    (`anchorTo`), or cover an area of a page (`coverArea`). All of it follows pan and zoom in the
    draw and layout phases, **without recomposing**.

  `PdfViewerState` is the public state, with `hitTest`, `panBy`, `animateScrollTo`,
  `pageRectInViewer` and more. Keep new engine API generic in the same way.
- **Page content comes from one binding**, `feature/pdfviewer/di/PageContentModule.kt`:
  `LayeredPageContentProvider(PlatformPageContentProvider, recognized = null)`.
  - Text recognition (OCR) will go in as `recognized`, from its own module.
  - The platform provider needs API 35+. Below that, every page is `Unsupported` and the viewer
    explains why (decision D1).
  - The platform's content APIs (`getTextContents`, `selectContent`, `getLinkContents`, the
    `android.graphics.pdf.models` types) are used **only** in `PlatformPageContentProvider`.
    Anywhere else, `PdfRenderer` is only for counting pages.
- **A `PdfRenderer` is never built with its constructor**, in the app or in a test. It is opened
  with `PdfRenderers.open`, or opened, used and closed with `PdfRenderers.use` (`:composepdf`). On
  Android 7 a document that fails to open, or two documents drawn at once, crash the process in
  native code; see [docs/pdf-engine.md](docs/pdf-engine.md#android-7).
- **Selection logic is pure and tested; the UI only draws it.**
  - `TextSelection` and `DocumentSelection`, in `:document-content-api`, work in carets over the
    page's text, character by character, across pages, and handle vertical and multi-column
    layouts.
  - `SelectionInteraction`, in the viewer, decides what a touch does.
- **Nothing opens from a PDF without `ResolveLinkUseCase`.**
  - Only `http`, `https`, `mailto` and `tel` are followed.
  - A web link is shown by the host a browser would really open (WHATWG parsing, punycode).
  - The preview comes before anything opens (D3).
  - Opening goes through `LinkOpener`, from the activity: a Custom Tab, then `ACTION_VIEW`, then a
    message (D4).
- **Display settings follow decision D2:**
  - optional app-wide defaults, set in Settings;
  - otherwise, per-document memory for the session: `ViewerSessionSettings`, plus the
    `SavedStateHandle` for process death.

  The factory setting is fit to width.

---

## 4. Critical flows

### Scan → save → Home

1. `HomeViewModel.startScan()` calls `DocumentScanner.scan()` and suspends in `viewModelScope`.
   The Home widget arrives through `ACTION_SCAN_DOCUMENT` in `MainActivity`, then `ScanRequestBus`,
   which the ViewModel also collects. `ScanRequestNavigation` brings Home to the front first.
2. `MlKitDocumentScanner` gets the `IntentSender` and launches it through `ActivityResultHost`.
   `MainActivity` attaches its launcher, and restores a launch that was pending across
   recreation. The result is mapped to a `ScanOutcome`.
3. The scanner outlives the process, so `HomeViewModel` records `scan_in_flight` in its
   `SavedStateHandle`. On restore, it rejoins through `DocumentScanner.resumePendingScan()`.
4. `SaveScanDraftUseCase` stores the file through `DocumentStorage` (app files, exposed through the
   `FileProvider`) and catalogues it in Room.
5. Home observes `ObserveDocumentsUseCase`. `HomeViewModel.observeDocuments` hands each change to
   `ProcessDocumentsUseCase`, which searches, filters and sorts.

### Opening a document

- **A catalogued document**: the `PdfViewer(uuid)` key, in the main stack. On a wide window it
  shows beside Home (list-detail).
- **Another app's PDF**: `PdfViewerActivity` takes `VIEW` and `SEND` for `application/pdf`.
  - It runs in its own task (`taskAffinity=""`, `autoRemoveFromRecents`) with its own back stack,
    rooted at `ExternalPdfViewer(uri, displayName)`.
  - Closing it returns to the calling app, not to Docucraft.
- Both go through `PdfViewerViewModel`, keyed by `ViewerDocumentRef` (`Catalogued` or `External`).

### Reading a page's text and links

1. The screen reports the visible pages after 150 ms without movement (`VisiblePagesChanged`), so
   a fling only reads where it stops.
2. The ViewModel reads those pages ±1 through one content session per document, and keeps their
   `pageText` and `pageLinks`.
3. A long press or a tap is decided synchronously from what has already been read.

---

## 5. Integrations and sensitive points

- **File sharing** uses `${applicationId}.fileprovider` (the manifest and `App.getAuthority`). Only
  `content://` locations are ever handed to another app (`canBeHandedOff`). A `file://` PDF opened
  from outside is shown, but never re-shared.
  - The provider is `CatalogueFileProvider`, which names a document as the catalogue does. It
    serves `documents/` (files named `<uuid>.pdf`) and `scans/pdf/` (documents saved before files
    were named by uuid, which are never moved).
- **Firebase Analytics and Crashlytics** are on (`core/di/AnalyticsModule.kt`,
  `google-services.json`).
- **Room**:
  - `DocumentsDatabase` is currently version 5, in the file `scanned_pdfs.db`. Migrations are in
    `DocumentsDatabaseMigrations.kt`. Build it with `DocumentsDatabase.builder`, in tests too.
  - Schemas are exported to `app/schemas/`. A schema change means: bump the version, add a
    migration, and commit the new schema JSON.
  - **Never a destructive fallback.** A migration keeps every document and its uuid, and only
    touches the database: it moves no files.
  - Room cannot declare a `CHECK`. Rules the tables cannot state are triggers in
    `DatabaseTriggers.ALL`, the one list used for a new database and by migrations, so the two
    cannot differ. `SchemaParityTest` compares them.
  - The SQL has to run on API 24's SQLite (3.9): no UPSERT, window functions, generated columns or
    `RENAME COLUMN`, and no `WITH` inside a trigger.
  - The database keeps paths relative to the files directory, never `FileProvider` URIs or
    absolute paths.
- **`<queries>` in the manifest** declares which other apps the viewer may look for: Custom Tabs,
  browsers, email, dialler. A new intent to another app needs its entry there, or on API 30+ the app
  will seem not to exist.
- **Known gaps, so they are not mistaken for regressions:**
  - `PdfViewerActivity` advertises `http`/`https` PDFs, but loads through the `ContentResolver`,
    so a remote PDF ends on the error screen. The engine can download (`PdfSource.Remote`); the app
    does not use it yet.
  - Internal links inside a PDF are never reported by the platform (`getGotoLinks()` is empty).
    The code is ready for them.
  - A link that spans two lines comes as one rectangle that may also cover nearby text.
  - `./gradlew :app:lintDebug` fails on `MissingTranslation`: most strings exist only in English
    and Spanish.

---

## 6. Build, test, verify

| What | Command (Windows: `.\gradlew.bat …`) |
|---|---|
| Debug APK | `./gradlew :app:assembleDebug` |
| Unit tests, every module | `./gradlew testDebugUnitTest :scanner-api:test :document-content-api:test` |
| Instrumented tests | `./gradlew :app:connectedDebugAndroidTest :composepdf:connectedDebugAndroidTest` |
| Format, then verify | `./gradlew spotlessApply`, then `./gradlew spotlessCheck` |

- **Instrumented tests need a device, and run on every attached device.** A phone connected over
  wireless ADB counts, even if nothing shows it. The same goes for `installDebug`. To target one,
  set `ANDROID_SERIAL` (for example `ANDROID_SERIAL=emulator-5554`). Use an emulator unless a real
  device is really needed.
- **Test PDFs** live in `composepdf/src/androidTest/assets/fixtures/`, together with a
  `manifest.json` of what each one contains, down to the position of every word.
  - `testing/pdf-fixtures/generate_fixtures.py` regenerates them.
  - `:app`'s instrumented tests read the same folder.
  - **Never commit a document with personal data** as a fixture. Build an equivalent one with
    invented content.
- **Spotless** formats Kotlin with ktfmt (Kotlin-lang style) and adds the license header from
  `spotless/copyright.txt`. Run it before every commit; `spotlessCheck` fails on anything it would
  change.
- Named APK copies go to `app/build/outputs/apk_custom/<variant>/` (`buildSrc/CopyApkPlugin.kt`).

---

## 7. Conventions

- **Documentation**
  - Start at [`docs/README.md`](docs/README.md).
  - Everything is in English, and describes the app as it is now. When code changes, update the
    document that describes it. History lives in git, not in the docs.
  - Explain the why next to each decision. Code-level detail belongs in KDoc, not in the docs.
  - The decisions named in code comments (D1–D5, E1–E6) are listed in `docs/README.md`.
- **Work happens by stabilization**, one subsystem at a time, following the method in
  [`docs/README.md`](docs/README.md#how-a-subsystem-is-stabilized). Each one has its own branch
  (`refactor/<subsystem>`), merged through a PR.
- **Commits** follow `type(scope): summary` (`feat(pdfviewer): …`, `fix(content): …`,
  `docs(pdfviewer): …`). The body explains why.
- **Comments and KDoc say why**, not what. Public API gets KDoc. A comment about a past bug says
  what went wrong, in words: no bug numbers that only made sense in a deleted document.
