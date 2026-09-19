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
- Home list comes from `ObserveDocumentsUseCase`; query/filter/sort is finalized in `HomeViewModel.applyFiltersAndSort`.

## Architecture Rules
- The scanning engine is swapped at one line: the `DocumentScanner` binding in
  `feature/docscanner/di/DocumentScannerModule.kt`. ML Kit types exist only in `:scanner-mlkit`
  and cannot be imported from `:app` (enforced by the module graph, not by convention).
- Add business logic as use cases under `feature/docscanner/domain/usecase`, then inject in `feature/docscanner/di/ScannedDocumentModule.kt`.
- Navigation is typed (`Route` in `core/presentation/common/Route.kt`), rendered by `Navigator.kt` with Navigation 3 `NavDisplay`.
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
