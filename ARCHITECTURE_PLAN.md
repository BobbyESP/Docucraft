# Docucraft Architecture (Current State)

> In-depth per-subsystem analysis and migration plans live in [`docs/`](docs/README.md).
> This file stays a compact snapshot of the current state.

## 1) System Intent
- Docucraft is a local-first Android scanner: capture documents with ML Kit, persist PDF metadata in Room, and render documents in-app.
- The codebase is split into four Gradle modules:
  - `:app`: product app (storage, UI, navigation, widgets, analytics).
  - `:composepdf`: in-repo PDF rendering engine used by `feature/pdfviewer`.
  - `:scanner-api`: the scanning contract. Plain Kotlin, no Android, no engine.
  - `:scanner-mlkit`: the ML Kit implementation of that contract.

## 2) Module Boundaries
- `app/src/main/java/com/bobbyesp/docucraft/core`
  - Cross-feature services: preferences, notifications, analytics, file repository, shared presentation/navigation helpers.
- `app/src/main/java/com/bobbyesp/docucraft/feature/docscanner`
  - Owns Room persistence, use cases, Home screen state/actions/effects, widget scan entrypoint.
  - Consumes scanning through `:scanner-api`; it never names an engine.
- `scanner-api/src/main/kotlin/com/bobbyesp/scanner`
  - `DocumentScanner` plus its vocabulary (`ScanRequest`, `ScanOutcome`, `ScanDraft`,
    `ScanArtifact`, `ScanError`, `ScannerCapabilities`, `ContentRef`). Zero dependencies.
- `scanner-mlkit/src/main/kotlin/com/bobbyesp/scanner/mlkit`
  - `MlKitDocumentScanner`, `ActivityResultHost` and the result mapping. The GMS dependency is
    `implementation`, so ML Kit never reaches `:app`'s compile classpath.
- `app/src/main/java/com/bobbyesp/docucraft/feature/pdfviewer`
  - Owns viewer screen and controls only; delegates PDF rendering to `:composepdf`.
- `composepdf/src/main/kotlin/com/composepdf`
  - Independent rendering engine (state, controller, layout, scheduler, tile cache, session coordination).

## 3) Dependency Injection and Composition Root
- Koin starts in `app/src/main/java/com/bobbyesp/docucraft/App.kt` via `startKoin`.
- Modules wired there are the source of truth for runtime graph:
  - Core: `commonModule`, `notificationsServiceModule`, `fileManagementModule`, `analyticsModule`.
  - Scanner feature: `scannedDocumentsDatabaseModule`, `documentScannerDataModule`, `documentScannerModule`, `documentScannerViewModels`.
- Rule: when adding a repository/use case/service, define it in the owning module and register it in the appropriate Koin module.

## 4) Navigation Model
- Navigation uses Navigation 3 typed keys, not graph XML and not Navigation Compose destinations.
- `Route` (`core/presentation/common/Route.kt`) is the typed contract.
- `Navigator.kt` renders routes with `NavDisplay` + `entryProvider`.
- `TopLevelBackStack` maintains top-level stack state and restoration (`rememberTopLevelBackStack`).

## 5) Critical Runtime Flow: Scan -> Persist -> UI
1. UI emits `HomeIntent.LaunchScanner` from `HomeScreen` to `HomeViewModel` (or the widget
   arrives through `ScanRequestBus`).
2. `HomeViewModel.startScan()` guards re-entrancy, then calls `documentScanner.scan()` and
   suspends. The session lives in `viewModelScope`, so it survives the scanner covering the app
   and the Activity being recreated.
3. `MlKitDocumentScanner` gets the IntentSender, launches it via `ActivityResultHost`, and maps
   what comes back to `ScanOutcome`: `Completed`, `Cancelled` or `Failed`.
4. On `Completed`, `SaveScanDraftUseCase` stores the file through `DocumentStorage`
   (`files/scans/pdf` + `FileProvider` URI + preview) and catalogues a `NewScannedDocument`.
5. `observeDocumentsUseCase()` updates the Home list; filtering and sorting happen in
   `HomeViewModel.observeDocuments`.

## 6) Persistence Model
- Room DB: `DocumentsDatabase` (`feature/docscanner/data/db/DocumentsDatabase.kt`), schema export enabled.
- DAO: `ScannedDocumentDao` provides observe/search/update/delete operations.
- Repository adapter: `LocalDocumentsRepositoryImpl` maps entities to domain models.
- DB versioning currently uses AutoMigration (`1 -> 2`); schema artifacts live in `app/schemas/...`.

## 7) Integration Points and Sensitive Areas
- The scanning engine is chosen in one place: the `DocumentScanner` binding in
  `feature/docscanner/di/DocumentScannerModule.kt`.
- `FileProvider` authority is `${applicationId}.fileprovider` (`AndroidManifest.xml`, `App.getAuthority`).
- Export/share paths rely on `ExportDocumentUseCase`, `ShareDocumentUseCase`, and persisted provider URIs.
- Firebase Analytics + Crashlytics are enabled in app build and injected via `AnalyticsModule.kt`.
- Home widget triggers scan via `ACTION_SCAN_DOCUMENT` into `MainActivity`.

## 8) Practical Change Rules
- Keep `:scanner-api` free of Android and of engine types; that module having no dependencies is
  what makes the rule enforceable rather than aspirational.
- Add feature logic as domain use cases first, then compose them in ViewModel.
- For navigation payload changes, update `Route` types and all callsites together.
- For Room entity/schema changes, update migration + exported schemas in the same change.
- Treat `:composepdf` as an internal engine boundary: prefer API-level integration from `feature/pdfviewer` instead of leaking engine internals into app feature code.
