# AGENTS.md

## Project Snapshot
- Multi-module Android project: `:app` (product) + `:composepdf` (local PDF engine).
- Stack in use: Kotlin, Jetpack Compose, Navigation 3 typed routes, Koin DI, Room, ML Kit Document Scanner.
- Runtime DI entrypoint is `app/src/main/java/com/bobbyesp/docucraft/App.kt` (`startKoin`).

## Where to Work
- Scanner feature lives in `app/src/main/java/com/bobbyesp/docucraft/feature/docscanner` (`data/domain/presentation/di`).
- Viewer feature lives in `app/src/main/java/com/bobbyesp/docucraft/feature/pdfviewer` and consumes `com.composepdf.PdfViewer`.
- Shared app services live in `app/src/main/java/com/bobbyesp/docucraft/core` (preferences, notifications, analytics, file ops, navigation helpers).
- Rendering engine internals live in `composepdf/src/main/kotlin/com/composepdf`.

## Critical Flow (Scan -> Save -> Home)
- `HomeViewModel` emits scan request through `ScannerManager.requestScan()`.
- `MainActivity` listens to `scannerManager.scanRequest` and launches `GmsDocumentScanner`.
- Activity result is mapped by `MlKitScannerRepository.processResult`.
- Result is returned to ViewModel via `scannerManager.onScanResult(...)`.
- `SaveScannedDocumentUseCase` persists PDF + thumbnail and inserts Room entity.
- Home list comes from `ObserveDocumentsUseCase`; query/filter/sort is finalized in `HomeViewModel.applyFiltersAndSort`.

## Architecture Rules
- Keep Android framework boundaries explicit: Activity launches scanner intent; ViewModel stays scanner-API-agnostic via `ScannerManager`.
- Add business logic as use cases under `feature/docscanner/domain/usecase`, then inject in `feature/docscanner/di/ScannedDocumentModule.kt`.
- Navigation is typed (`Route` in `core/presentation/common/Route.kt`), rendered by `Navigator.kt` with Navigation 3 `NavDisplay`.
- App-wide settings and services should flow via composition locals in `core/presentation/common/CompositionLocals.kt`.

## Integrations and Sensitive Points
- ML Kit options are configured in `feature/docscanner/di/GmsScannerModule.kt` (`RESULT_FORMAT_PDF`, `SCANNER_MODE_FULL`).
- File sharing relies on `${applicationId}.fileprovider` (`AndroidManifest.xml` + `App.getAuthority`).
- Firebase Analytics/Crashlytics are enabled (`core/di/AnalyticsModule.kt`, `app/build.gradle.kts`, `google-services.json`).
- Home widget scan action enters app through `ACTION_SCAN_DOCUMENT` in `MainActivity`.
- Room schema export is active; keep `app/schemas/...` updated when changing DB entities/migrations.

## Build and Validation
- Debug APK: `./gradlew :app:assembleDebug` (Windows: `.\gradlew.bat :app:assembleDebug`).
- Unit tests: `./gradlew :app:testDebugUnitTest :composepdf:testDebugUnitTest`.
- Instrumented tests: `./gradlew :app:connectedDebugAndroidTest :composepdf:connectedDebugAndroidTest`.
- Formatting: `./gradlew ktfmtFormat` (convention plugin applies `ktfmt` to modules).
- Custom APK copies are generated under `app/build/outputs/apk_custom/<variant>/` by `buildSrc/CopyApkPlugin.kt`.
