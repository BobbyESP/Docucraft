<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Scanning and the catalogue

How a scan becomes a document the app owns, and how the catalogue keeps, finds and removes
documents. Code: `:scanner-api`, `:scanner-mlkit` and `app/.../feature/docscanner`.

## The contract (`:scanner-api`)

```kotlin
interface DocumentScanner {
    val capabilities: ScannerCapabilities
    suspend fun scan(request: ScanRequest = ScanRequest()): ScanOutcome
    suspend fun resumePendingScan(): ScanOutcome? = null
}

sealed interface ScanOutcome { Completed(draft: ScanDraft) · Cancelled · Failed(error: ScanError) }
sealed interface ScanError   { EngineUnavailable · PermissionDenied · NoOutputProduced · Engine(cause) }
```

**The key decision: one suspending call.** The ML Kit scanner is launched with an `IntentSender`
and answers through an activity result, so a scan is naturally split in two halves. The contract
hides that. The caller writes `val outcome = scanner.scan()` and gets one answer.

- **Cancelling is a result, not an error.** Treating it as a failure is how a cancelled scan used to
  be shown to the user as an error.
- **The contract carries nothing the current engine does not need, with two exceptions**, kept so
  that another engine needs no contract change:
  - `ScanArtifact.Pages`, for engines that return images;
  - `requiresCameraPermission`, for an in-app CameraX engine.
- **`ContentRef`** is the contract's locator: a URI as a string. It keeps `android.net.Uri` out of
  every domain.

## The ML Kit engine (`:scanner-mlkit`)

- **`MlKitDocumentScanner`** is the only class that knows ML Kit. It builds the options from the
  `ScanRequest` and gets the `IntentSender`. It launches it through an `ActivityResultHost`, and
  turns the result into a `ScanOutcome`.
- **`ScanResultMapper`** is the decision table for what a raw result means: finished, cancelled, or
  finished without output. It is pure, so it is tested on the JVM.
- **`ActivityResultHost`** is the slice of the activity a component below the UI needs. Only an
  activity can register for results, so `MainActivity` registers a launcher and lends it to the
  process-scoped `ActivityResultHostImpl`. It attaches the host on create and detaches it on
  destroy. A caller that arrives while no activity is attached, such as during a rotation,
  suspends until one is.

### Surviving process death

The scanner runs in Play Services' process, so the system may kill Docucraft while the user is
still scanning.
- **The result is not lost.** `registerForActivityResult` re-delivers it to the recreated activity.
  `ActivityResultHostImpl` buffers a result that arrives with nobody waiting.
- **Someone comes back for it.** `HomeViewModel` records `scan_in_flight` in its
  `SavedStateHandle`. On restore, it calls `DocumentScanner.resumePendingScan()`, which returns the
  buffered result.
- **A stale flag cannot hang the spinner.** The process might have died *before* anything was
  launched. So whether a launch is pending travels in the activity's own saved state, next to the
  result registry's pending request: both survive, or neither does. With nothing pending,
  `resumePendingScan()` returns `null` at once. The registry can also re-deliver before the
  activity restores that flag; `restorePendingLaunch` does not overwrite a result already received.

Swapping the engine means changing the `DocumentScanner` binding in
`feature/docscanner/di/DocumentScannerModule.kt`.

## Entry points

- **Home.** `HomeIntent.LaunchScanner` leads to `HomeViewModel.startScan()`, which guards against
  re-entry and suspends in `viewModelScope` until the outcome arrives.
- **The home-screen widget** sends `ACTION_SCAN_DOCUMENT` to `MainActivity`.
  - `MainActivity` puts a request on `ScanRequestBus`. That is a *standing* request, not a one-shot
    signal, because two parts act on it:
    - `ScanRequestNavigation`, in the shell, brings Home to the front;
    - `HomeViewModel` takes the request and scans.
  - With a one-shot signal only the first reader learnt of it. A widget tap while a document was
    open did nothing, and then scanned, unasked, when the user pressed back.

## Saving a scan

`SaveScanDraftUseCase` only fixes the order:
1. store the file;
2. confirm it is real;
3. try for a preview;
4. catalogue the document.

`DocumentStorage` decides where things go: it copies the scanner's short-lived file into the app's
files directory (`scans/pdf/`) and exposes it as a `FileProvider` URI. The preview is a WEBP in
`previews/`.

The save reports its own failure. Earlier, a failed save still congratulated the user.

## The catalogue

- **Room.** `DocumentsDatabase`, currently version 5, in the file `scanned_pdfs.db`. A scan is a
  row of `documents` with custody `MANAGED` and origin `SCAN`, plus a row of `document_activity`
  and one row of `pages` for each of its pages. `documents_fts` is the FTS4 index over what a
  document is called and described as; its `unicode61` tokenizer ignores case and accents.
  - The catalogue keeps a document's path relative to the files directory, never a `FileProvider`
    URI, which depends on the authority. `DocumentLocations` builds the URI when it is asked for.
  - Column names are the schema. `ScannedDocumentMapper` translates them to the domain's
    `ScannedDocument`.
  - Rules the tables cannot state are triggers, in `DatabaseTriggers.ALL`. A new database gets
    them when it is created and an old one from the migration, from that same list.
  - Migrations are in `DocumentsDatabaseMigrations.kt`, and schemas are exported to
    `app/schemas/`. A schema change means: bump the version, add the migration, and commit the new
    schema JSON. There is no destructive fallback: a migration keeps every document and its uuid.
- **Home's list.** `ObserveDocumentsUseCase` feeds `HomeViewModel.observeDocuments`, which combines
  it with the search query (debounced 150 ms) and the filters. It then hands everything to
  `ProcessDocumentsUseCase`, in three steps:
  - **search**, through the FTS table first, falling back to an in-memory search when the query
    fails or matches nothing;
  - **filter**;
  - **sort**.
- **Document actions.** Actions, Edit and Delete are destinations with their own keys (see
  [navigation.md](navigation.md)), backed by `DocumentActionsViewModel`.
- **Deleting.** `DeleteDocumentUseCase` removes the catalogue row first, then the file and its
  preview. A row pointing at a missing file is visible to the user; an orphan file is not.
- **Export and share.**
  - `DocumentExporter` (FileKit) copies a document where the user chooses. It returns
    `Saved` / `Cancelled` / `Failed`, shaped like a scan.
  - `DocumentSharer` shares its `FileProvider` URI.

## Not yet verified on a device

The process-death paths are covered by `ActivityResultHostImplTest`, but not by a real kill:
1. Start a scan, then run `adb shell am kill com.bobbyesp.docucraft.debug`. Finish the scan: the
   document must appear saved.
2. The same with "Don't keep activities" on.
3. Kill before the scanner shows: the spinner must stop on its own.
