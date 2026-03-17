# Docucraft Architecture (Current State)

## 1) System Intent
- Docucraft is a local-first Android scanner: capture documents with ML Kit, persist PDF metadata in Room, and render documents in-app.
- The codebase is split into two Gradle modules:
  - `:app`: product app (scanning, storage, UI, navigation, widgets, analytics).
  - `:composepdf`: in-repo PDF rendering engine used by `feature/pdfviewer`.

## 2) Module Boundaries
- `app/src/main/java/com/bobbyesp/docucraft/core`
  - Cross-feature services: preferences, notifications, analytics, file repository, shared presentation/navigation helpers.
- `app/src/main/java/com/bobbyesp/docucraft/feature/docscanner`
  - Owns scanner integration, Room persistence, use cases, Home screen state/actions/effects, widget scan entrypoint.
- `app/src/main/java/com/bobbyesp/docucraft/feature/pdfviewer`
  - Owns viewer screen and controls only; delegates PDF rendering to `:composepdf`.
- `composepdf/src/main/kotlin/com/composepdf`
  - Independent rendering engine (state, controller, layout, scheduler, tile cache, session coordination).

## 3) Dependency Injection and Composition Root
- Koin starts in `app/src/main/java/com/bobbyesp/docucraft/App.kt` via `startKoin`.
- Modules wired there are the source of truth for runtime graph:
  - Core: `commonModule`, `notificationsServiceModule`, `fileManagementModule`, `analyticsModule`.
  - Scanner feature: `scannedDocumentsDatabaseModule`, `documentScannerDataModule`, `gmsScannerModule`, `documentScannerViewModels`, `mlKitModule`.
- Rule: when adding a repository/use case/service, define it in the owning module and register it in the appropriate Koin module.

## 4) Navigation Model
- Navigation uses Navigation 3 typed keys, not graph XML and not Navigation Compose destinations.
- `Route` (`core/presentation/common/Route.kt`) is the typed contract.
- `Navigator.kt` renders routes with `NavDisplay` + `entryProvider`.
- `TopLevelBackStack` maintains top-level stack state and restoration (`rememberTopLevelBackStack`).

## 5) Critical Runtime Flow: Scan -> Persist -> UI
1. UI emits `HomeUiAction.LaunchDocumentScanner` from `HomeScreen` to `HomeViewModel`.
2. `HomeViewModel` calls `scannerManager.requestScan()`.
3. `MainActivity` collects `scannerManager.scanRequest` and launches `GmsDocumentScanner` intent sender.
4. Activity result is mapped in `MlKitScannerRepository.processResult` to `RawScanResult`.
5. `MainActivity` pushes success/failure back via `scannerManager.onScanResult(...)`.
6. `HomeViewModel` collects `scanResult`; on success it executes `SaveScannedDocumentUseCase`.
7. `SaveScannedDocumentUseCase` copies PDF to app files (`files/scans/pdf`), generates thumbnail, creates `FileProvider` URI, inserts `ScannedDocumentEntity`.
8. `observeDocumentsUseCase()` updates Home list; filtering/sorting is applied in `HomeViewModel.applyFiltersAndSort`.

## 6) Persistence Model
- Room DB: `DocumentsDatabase` (`feature/docscanner/data/db/DocumentsDatabase.kt`), schema export enabled.
- DAO: `ScannedDocumentDao` provides observe/search/update/delete operations.
- Repository adapter: `LocalDocumentsRepositoryImpl` maps entities to domain models.
- DB versioning currently uses AutoMigration (`1 -> 2`); schema artifacts live in `app/schemas/...`.

## 7) Integration Points and Sensitive Areas
- ML Kit scanner client/options in `feature/docscanner/di/GmsScannerModule.kt` (`RESULT_FORMAT_PDF`, `SCANNER_MODE_FULL`).
- `FileProvider` authority is `${applicationId}.fileprovider` (`AndroidManifest.xml`, `App.getAuthority`).
- Export/share paths rely on `ExportDocumentUseCase`, `ShareDocumentUseCase`, and persisted provider URIs.
- Firebase Analytics + Crashlytics are enabled in app build and injected via `AnalyticsModule.kt`.
- Home widget triggers scan via `ACTION_SCAN_DOCUMENT` into `MainActivity`.

## 8) Practical Change Rules
- Keep Android framework concerns in Activity (scanner intent/result); keep ViewModel scanner-API-agnostic via `ScannerManager`.
- Add feature logic as domain use cases first, then compose them in ViewModel.
- For navigation payload changes, update `Route` types and all callsites together.
- For Room entity/schema changes, update migration + exported schemas in the same change.
- Treat `:composepdf` as an internal engine boundary: prefer API-level integration from `feature/pdfviewer` instead of leaking engine internals into app feature code.
