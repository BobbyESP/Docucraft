# AGENTS.md

## Project Snapshot
- Deep-dive architecture docs and stabilization plans: `docs/` (start at `docs/README.md`).
- Multi-module Android project: `:app` (product), `:composepdf` (local PDF engine),
  `:scanner-api` (engine-agnostic scanning contract, plain Kotlin), `:scanner-mlkit` (ML Kit
  implementation of it).
- Stack in use: Kotlin, Jetpack Compose, Navigation 3 typed routes, Koin DI, Room, ML Kit Document Scanner.
- Runtime DI entrypoint is `app/src/main/java/com/bobbyesp/docucraft/App.kt` (`startKoin`).

## Where to Work
- Scanner feature lives in `app/src/main/java/com/bobbyesp/docucraft/feature/docscanner` (`data/domain/presentation/di`).
- Viewer feature lives in `app/src/main/java/com/bobbyesp/docucraft/feature/pdfviewer` and consumes `com.composepdf.PdfViewer`.
- Shared app services live in `app/src/main/java/com/bobbyesp/docucraft/core` (preferences, notifications, analytics, file ops, navigation helpers).
- Rendering engine internals live in `composepdf/src/main/kotlin/com/composepdf`.

## Critical Flow (Scan -> Save -> Home)
- `HomeViewModel.startScan()` calls `DocumentScanner.scan()` and suspends in `viewModelScope`.
- `MlKitDocumentScanner` (`:scanner-mlkit`) gets the IntentSender, launches it through
  `ActivityResultHost` and maps the result to a `ScanOutcome`.
- `MainActivity` only lends its activity result launcher to the host; it knows nothing about
  scanning.
- The widget enters through `ScanRequestBus`, which the ViewModel also collects.
- `SaveScanDraftUseCase` stores the file via `DocumentStorage` and catalogues it.
- The scanner outlives this process, so `HomeViewModel` records `scan_in_flight` in its
  `SavedStateHandle` and rejoins through `DocumentScanner.resumePendingScan()` on restore.
- Home list comes from `ObserveDocumentsUseCase`; query/filter/sort is finalized in `HomeViewModel.applyFiltersAndSort`.

## Architecture Rules
- The scanning engine is swapped at one line: the `DocumentScanner` binding in
  `feature/docscanner/di/DocumentScannerModule.kt`. ML Kit types exist only in `:scanner-mlkit`
  and cannot be imported from `:app` (enforced by the module graph, not by convention).
- Add business logic as use cases under `feature/docscanner/domain/usecase`, then inject in `feature/docscanner/di/ScannedDocumentModule.kt`.
- Navigation is one back stack rendered by one Navigation 3 `NavDisplay`
  (`core/presentation/navigation/DocucraftApp.kt`). Keys are typed and `@Serializable`, and each
  feature owns its own (`feature/*/navigation/*Keys.kt`); `core/presentation/screens/preferences/navigation/SettingsKeys.kt`
  for settings. Features never touch the stack — they get a `Navigator`
  (`core/presentation/navigation/Navigator.kt`).
- **Modal destinations go on that same back stack. Never put a `NavDisplay` inside a sheet or a
  dialog for them.** A sheet or dialog the user can reach, leave, and come back to is a
  destination: give it a key and let `OverlaySceneStrategy`
  (`core/presentation/navigation/overlay/`) choose its container. A nested display is only for a
  self-contained flow that is discarded whole and survives nothing. See
  [docs/architecture/05-navigation-audit.md](docs/architecture/05-navigation-audit.md#decisión-un-navdisplay-dentro-de-un-modal)
  for why — it is the decision that caused the most bugs in this codebase.
- A destination is told about its surroundings, it never measures them. `LocalPaneContext` says
  whether it shares the window; `LocalOverlayContext` says which container it landed in and whether
  there is room to stack. Reading `currentWindowAdaptiveInfo` or the device orientation from a
  screen is a bug, not a shortcut.
- Transitions live in one file (`core/presentation/navigation/motion/NavigationMotion.kt`). Screens
  contribute nothing to them.
- App-wide settings and services should flow via composition locals in `core/presentation/common/CompositionLocals.kt`.

## Integrations and Sensitive Points
- ML Kit options are derived from a `ScanRequest` in `scanner-mlkit`'s `MlKitDocumentScanner`.
- File sharing relies on `${applicationId}.fileprovider` (`AndroidManifest.xml` + `App.getAuthority`).
- Firebase Analytics/Crashlytics are enabled (`core/di/AnalyticsModule.kt`, `app/build.gradle.kts`, `google-services.json`).
- Home widget scan action enters app through `ACTION_SCAN_DOCUMENT` in `MainActivity`.
- Room schema export is active; keep `app/schemas/...` updated when changing DB entities/migrations.

## Build and Validation
- Debug APK: `./gradlew :app:assembleDebug` (Windows: `.\gradlew.bat :app:assembleDebug`).
- Unit tests: `./gradlew testDebugUnitTest :scanner-api:test` (all modules).
- Instrumented tests: `./gradlew :app:connectedDebugAndroidTest :composepdf:connectedDebugAndroidTest`.
- Formatting: `./gradlew spotlessApply` (Spotless applies `ktfmt` to modules; `spotlessCheck` verifies).
- Custom APK copies are generated under `app/build/outputs/apk_custom/<variant>/` by `buildSrc/CopyApkPlugin.kt`.
