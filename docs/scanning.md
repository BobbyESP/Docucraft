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

`SaveScanDraftUseCase` only fixes the order, and the order is the rule: **the file first, the
catalogue after**. A document is never listed before it can be opened.
1. **Store the file.** `DocumentStorage` copies the scanner's short-lived file to
   `documents/<uuid>.pdf`. It writes under a temporary name and renames at the end, so a file with
   its final name is whole even if the process dies during the copy. An empty file is refused. The
   copy also yields the file's size, its SHA-256 and its page count.
2. **Catalogue it.** `DocumentsRepository.addScan` writes the document, its activity and one page
   per page in a single transaction.
3. **If cataloguing fails, take the file back out.**

- **The file is named after the document's uuid**, which is given before the file is written.
  Named after the scan, two scans in the same second overwrote each other's file, and renaming a
  document would have meant moving it.
- **Documents saved before that stay in `scans/pdf/`** under their old name. The catalogue says
  where each document is, so nothing needs them moved, and the provider serves both folders.
- **The page count is the scanner's**, and when the scanner reports none, the one counted in the
  file. A file with neither is not catalogued.

**A preview is not saved with the document.** It is a picture of its first page that can be drawn
again at any time, so it lives in a cache (`DocumentThumbnails`, in the cache directory) and the
catalogue keeps nothing about it. A screen hands the image loader the document's `DocumentThumbnail`,
which names the document and the version of its content, and the preview is drawn the first time
it is asked for. That one is missing is never an error: the screen shows a placeholder.

The save reports its own failure. Earlier, a failed save still congratulated the user.

## The catalogue

- **Room.** `DocumentsDatabase`, currently version 5, in the file `scanned_pdfs.db`. A scan is a
  row of `documents` with custody `MANAGED` and origin `SCAN`, plus a row of `document_activity`
  and one row of `pages` for each of its pages. `documents_fts` is the FTS4 index over what a
  document is called and described as; its `unicode61` tokenizer ignores case and accents.
  - The catalogue keeps a document's path relative to the files directory, never a `FileProvider`
    URI, which depends on the authority. `DocumentLocations` builds the URI when it is asked for.
  - Column names are the schema. `DocumentMapper` translates them to the domain's
    `Document`.
  - Rules the tables cannot state are triggers, in `DatabaseTriggers.ALL`. A new database gets
    them when it is created and an old one from the migration, from that same list.
  - Migrations are in `DocumentsDatabaseMigrations.kt`, and schemas are exported to
    `app/schemas/`. A schema change means: bump the version, add the migration, and commit the new
    schema JSON. There is no destructive fallback: a migration keeps every document and its uuid.
- **Home's list.** `ObserveDocumentsUseCase` feeds `HomeViewModel.observeDocuments`, which combines
  it with the filters and hands both to `ProcessDocumentsUseCase`: **filter**, then **sort**.
- **Recents.** The shelf at the top of Home is the documents used last
  (`ObserveRecentDocumentsUseCase`), whatever the list below is sorted by.
  - **A document is recent because it was opened, or because it is new.** `document_activity`
    keeps `last_opened_at` and `last_activity_at`, the later of when the document entered the
    catalogue and when it was last opened. It is stored, although it can be derived, because
    Recents is ordered by it and needs the index. The statement that notes an open writes both.
  - **The viewer says when a document is opened**, once each time (`RecordDocumentOpenedUseCase`),
    and whether its file was there (`RecordDocumentAvailabilityUseCase`): only whoever has just
    tried to read a file knows. A document whose file could not be reached is shown faded on the
    shelf, so that it says so before it is tapped.
  - **Activity is a table of its own** (`DocumentActivityRepository`), because it is written by
    reading: it also keeps where each document was left
    ([pdf-viewer.md](pdf-viewer.md#reading-position)). In `documents`, each of those writes would
    re-index the document and make every list of the library emit.
  - **The bin is not recent, and other apps' documents are.** With three documents or fewer in the
    library the shelf would only repeat the list, and is left out, unless it holds a document of
    another app: those are in no list, and the shelf is the only way back to them.
- **Search.** `DocumentSearchViewModel` combines the library with the query (debounced 150 ms) and
  hands both to `SearchDocumentsUseCase`. The results are in order of relevance, not in the list's
  order, and each says on which page the match is and shows the words around it when the match is
  in the document's text. See [Search](#search).
- **Document actions.** Actions, Edit and Delete are destinations with their own keys (see
  [navigation.md](navigation.md)), backed by `DocumentActionsViewModel`.
- **Deleting.** `DeleteDocumentUseCase` removes the catalogue row first, then the file, and
  forgets its previews. A row pointing at a missing file is visible to the user; an orphan file is
  not.
- **Folders, tags and pages.** Their models and ports are in the domain (`FoldersRepository`,
  `TagsRepository`, `PagesRepository`) and are implemented over Room. No screen uses them yet.
  - **What the user can cause comes back as an answer**: a name that is taken, a folder moved into
    itself. `FolderChange` and `TagChange` are sealed, and the repository checks the rule and makes
    the change in one transaction. The triggers and indices stay as the net under it.
  - **Names are compared normalized** (`normalizedNameOf`): trimmed, lower case, without accents.
    "Facturas" and "facturas " are one folder among its siblings, and one tag.
  - **Deleting a folder deletes no document.** Its documents, the bin's included, and its
    subfolders go to the folder it was in; a subfolder that lands where its name is taken becomes
    "Name (2)".
  - **A folder cannot contain itself.** The trigger only sees a folder that is its own parent,
    because SQLite 3.9 cannot walk the ancestors inside a trigger; a longer loop is stopped by the
    repository (`FolderTree.wouldContainItself`).
- **Export and share.**
  - `DocumentExporter` (FileKit) copies a document where the user chooses. It returns
    `Saved` / `Cancelled` / `Failed`, shaped like a scan.
  - `DocumentSharer` shares its `FileProvider` URI.
  - `CatalogueFileProvider` is that provider. A plain `FileProvider` names a file by its name on
    disk, which is now a uuid; this one answers `DISPLAY_NAME` with the name the document has in
    the catalogue, so the receiving app shows what the user sees here.

## Documents of other apps

A PDF another app opens in the viewer becomes a document of the catalogue too, of the other kind:
`LINKED`. The app keeps a reference to it and nothing else, so that it can be found again in
Recents.

- **Its file is never the app's.** It is not copied, changed or deleted from here. Keeping it is
  something the user asks for.
- **Its location is its identity.** `RegisterLinkedDocumentUseCase` refers to the document the
  catalogue already has for that URI, or adds one: opening the same file ten times is one entry in
  Recents, with one reading position. The same file reached through two URIs is two documents.
- **It is not in the library.** No folder, no tags, no pages, no bin, and its text is not read. The
  triggers hold the row to that, and it shows in Recents only.
- **Permission** (`ExternalDocumentAccess`). When it is registered, the app tries to keep the
  permission to read it after the other app is gone. Most apps do not allow it, and a shared file
  never does: the document then opens only for as long as the loan lasts. Whether it was kept is
  noted, and what is held is given back when the document is forgotten.
- **No more than 50.** They are only ever listed in Recents, which shows a handful; without a limit
  they would pile up unseen, each holding a permission. Registering one over the limit forgets the
  ones used longest ago, in the same transaction. The statement that removes them names the
  custody: no mistake above it can make it delete a document the app keeps.
- **What it turned out to be** is noted once it has been opened (`DescribeLinkedDocumentUseCase`):
  size, pages, a hash of its content, whether it asks for a password. It reads the whole file, so
  it runs after the document is on screen, and on every opening, since another app's file can be
  replaced. What could not be learnt one time is left as it was known.
- **Saving it** (`SaveLinkedToLibraryUseCase`) copies its file and turns the reference into a
  document the app keeps: custody `MANAGED`, origin `IMPORT`.
  - **It is the same document afterwards.** The row is changed in place, in one statement, so its
    uuid, when it was opened and where it was left stay as they were. Where it came from is kept
    in `source_uri`, and it gets the pages a kept document has.
  - **The file first, then the catalogue**, as when a scan is saved. If the catalogue then fails,
    the copy is removed: the library is never left with a file nothing refers to.
  - **The answer is a result** (`SaveToLibraryOutcome`): saved; not readable, for a file out of
    reach, protected or damaged, since the library only keeps what it can show; already in the
    library, when a document there has the same content (its hash); or nothing to save.
  - **A duplicate is asked about, not refused.** The copy made to compare it is removed, and the
    question is a destination of its own (`SaveCopyToLibrary`). Saying yes saves it.
  - **The same location opened after saving is a new linked document**: the reference went with
    the save, and the other app's file is still the other app's.
- **On the shelf** it has no preview: its file is not read to draw one. One that could not be
  reached the last time is faded, and tapping it offers what can be done about it, *Remove from
  Recents*, instead of an error (`ForgetLinkedDocumentUseCase`). Only the reference goes.

## Search

`SearchIndex` is the port: a text in, the documents that match out, best first. Its one
implementation, bound in `ScannedDocumentModule.kt`, is `Fts4SearchIndex`: SQLite's FTS4, which Room
supports and every device has. FTS5 would add substring and CJK search, as another implementation
and one line of DI.

- **Two indexes.** `documents_fts` covers what a document is called and described as: title,
  original name, suggested title, description and the PDF's author, subject and keywords.
  `page_texts_fts` covers the text of each page. Neither holds text of its own: they point at
  `documents` and `page_texts`, and Room keeps them in step with triggers.
- **`unicode61`.** The tokenizer ignores case and accents, in the index and in the query alike, so
  "cancion" finds "Canción".
- **What the user types is text, never syntax** (`Fts4Query`). FTS4 gives a meaning to quotes, `*`,
  `-`, `:` and the words `OR`, `NOT` and `NEAR`. The query is cut into words the way the tokenizer
  cuts a document, lowered, and limited to eight; each becomes a prefix, and they are joined with
  spaces, which requires them all. Never with `AND`: in the query syntax Android's SQLite is
  compiled with, that is a word to look for.
  - Accents are left in the terms. SQLite passes each term through the index's tokenizer; removing
    them in Kotlin as well would do it by other rules, and break the words where the two disagree.
- **Relevance** (`Bm25`). FTS4 has no ranking function, so the score is worked out in Kotlin from
  `matchinfo(…, 'pcnalx')`: Okapi BM25, with a weight for each column (title 10, original name 8,
  suggested title 6, description 4, PDF metadata 2, page text 1). A document scores its own row
  plus half its best page. Between equal scores, the document used most recently comes first.
- **Passages.** A match in a page comes with `snippet()`: about twelve words around it, and which
  of them matched. The marks around a match are control characters, taken out in `MarkedFragment`,
  so the brackets and asterisks a document has of its own stay text.
- **Only the library.** Both queries go through the `library_documents` view, which leaves out the
  bin and other apps' documents.
- **Limits.** FTS4 finds whole words and their beginnings, not text inside a word, and it does not
  split Chinese, Japanese or Korean into words.

## Not yet verified on a device

The process-death paths are covered by `ActivityResultHostImplTest`, but not by a real kill:
1. Start a scan, then run `adb shell am kill com.bobbyesp.docucraft.debug`. Finish the scan: the
   document must appear saved.
2. The same with "Don't keep activities" on.
3. Kill before the scanner shows: the spinner must stop on its own.
