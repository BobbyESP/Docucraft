<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Scanning and the catalogue

How a scan becomes a document the app owns, and how the catalogue keeps, finds and removes
documents. Code: `:scanner:api` and `:scanner:mlkit`, the catalogue in `:library:*`, and the
screens of `:feature:home` and `:feature:scan`.

## The contract (`:scanner:api`)

```kotlin
interface DocumentScanner {
    val capabilities: ScannerCapabilities
    suspend fun scan(request: ScanRequest = ScanRequest()): ScanOutcome
    suspend fun resumePendingScan(): ScanOutcome? = null
}

sealed interface ScanOutcome { Completed(draft: ScanDraft) · Cancelled · Failed(error: ScanError) }
sealed interface ScanError   { EngineUnavailable · PermissionDenied · NoOutputProduced · Engine(cause) }
```

**One suspending call.** ML Kit launches through an `IntentSender` and answers through an activity
result, so a scan is naturally two halves. The contract hides that: `val outcome = scanner.scan()`.

- **Cancelling is a result, not an error**, so it is never shown as a failure.
- **The contract carries only what the current engine needs, plus two things for another engine:**
  `ScanArtifact.Pages` (engines that return images) and `requiresCameraPermission` (an in-app
  CameraX engine).
- **`ContentRef`** is the contract's locator: a URI as a string, which keeps `android.net.Uri` out
  of every domain.

## The ML Kit engine (`:scanner:mlkit`)

- **`MlKitDocumentScanner`** is the only class that knows ML Kit: it builds the options from the
  `ScanRequest`, launches the `IntentSender` through an `ActivityResultHost` and maps the result to
  a `ScanOutcome`.
- **`ScanResultMapper`** is the pure decision table for a raw result (finished, cancelled, finished
  without output), tested on the JVM.
- **`ActivityResultHost`** is the slice of the activity that components below the UI need. Only an
  activity can register for results, so `MainActivity` lends its launcher to the process-scoped
  `ActivityResultHostImpl`, attaching on create and detaching on destroy. A caller that arrives
  while none is attached (a rotation) suspends until one is.

### Surviving process death

The scanner runs in Play Services' process, so the system may kill Docucraft mid-scan.
- **The result is not lost.** `registerForActivityResult` re-delivers it to the recreated activity,
  and `ActivityResultHostImpl` buffers a result that arrives with nobody waiting.
- **Someone comes back for it.** `HomeViewModel` records `scan_in_flight` in its
  `SavedStateHandle`; on restore it calls `DocumentScanner.resumePendingScan()`.
- **A stale flag cannot hang the spinner.** The process may have died *before* anything launched,
  so "a launch is pending" travels in the activity's saved state next to the result registry's
  pending request: both survive or neither does. With nothing pending, `resumePendingScan()`
  returns `null` at once. The registry may re-deliver before that flag is restored, so
  `restorePendingLaunch` never overwrites a result already received.

Swapping the engine means changing the `DocumentScanner` binding in `DocumentScannerModule.kt`
(`:app`, the only module that sees the engine).

## Entry points

- **Home.** `HomeIntent.LaunchScanner` calls `HomeViewModel.startScan()`, which guards against
  re-entry and suspends in `viewModelScope` until the outcome arrives.
- **The home-screen widget** sends `ACTION_SCAN_DOCUMENT` to `MainActivity`, which puts a request on
  `ScanRequestBus`. It is a *standing* request, not a one-shot signal, because two parts act on it:
  `ScanRequestNavigation` (the shell) brings Home to the front, and `HomeViewModel` scans. A
  one-shot signal reaches only the first reader.
  - Each takes its own half (`take`, `takeCatalogueWanted`). The catalogue's ViewModel listens
    whenever Home is in the back stack; with one shared flag it would take the request before the
    shell saw it, and the scan would run with another screen still in front.

## Saving a scan

`SaveScanDraftUseCase` fixes the order, and the order is the rule: **the file first, the catalogue
after**, so a document is never listed before it can be opened.
1. **Store the file.** `DocumentStorage` copies the scanner's short-lived file to
   `documents/<uuid>.pdf`, writing under a temporary name and renaming at the end, so a file with
   its final name is whole. An empty file is refused. The copy yields size, SHA-256 and page count.
2. **Catalogue it.** `DocumentsRepository.addScan` writes the document, its activity and one page
   row per page in a single transaction.
3. **If cataloguing fails, remove the file.** The save reports its own failure.

- **The file is named after the document's uuid**, given before the file is written. A name based on
  the scan could collide within a second, and renaming a document would mean moving its file.
- **Older documents stay in `scans/pdf/`** under their old name. The catalogue says where each is,
  and the provider serves both folders.
- **The page count is the scanner's**, else the one counted in the file; a file with neither is not
  catalogued.

**A preview is not saved with the document.** It is a picture of the first page that can be redrawn
at any time, so it lives in a cache (`DocumentThumbnails`) and the catalogue keeps nothing about it.
A screen gives the image loader the document's `DocumentThumbnail` (the document and the version of
its content), and the preview is drawn on first request. A missing one is never an error: the screen
shows a placeholder.

## Reviewing a scan

As the scanner closes, the scan is shown to be named and organized (`presentation/screens/review`):
a wide card with its first page, name and what is known of it; title and description; and a grouped
list with its folder, tags and whether its text is recognized.

- **Built from Home's parts** so the screen reads as the same app: Home's app bar, section headers
  and grouped list, and *Skip* and *Save* floating at the bottom with a blur halo. Folder and tags
  show their own badge, color and dots; the card takes the theme's color, as a folder without one
  does.
- **`DocumentHeroCard`** (`:library:ui`) names the document as it is typed. The viewer's details
  use it too, so a document is introduced the same way in both.
- **Title and description use the fields that edit a document later**
  (`EditDocumentDetailsContent`), with the same limits, so a title that fits here fits there.
- **Folder and tags use the overlays of the document's actions**, which open over the review and
  return to it.
- **On a wide window the form keeps to a column.**
- **The scan is saved first, reviewed after.** The review edits a document already in the library
  through the operations its actions use (`UpdateDocumentFieldsUseCase`, the folder picker, the tags
  sheet, `SetDocumentTextRecognitionUseCase`). Nothing waits in memory, so a process that dies
  during the review, *Skip*, and turning the review off (`UserPreferences.reviewNewScans`) all leave
  the document saved as it came.
- **It is a destination**, `ReviewScan(documentUuid)`, rising over Home with `RisingMotion`
  ([navigation.md](navigation.md#the-model)) because it interrupts rather than follows.
  `HomeViewModel` keeps the uuid in its state and `SavedStateHandle` (`scanToReview`) and Home opens
  the review and says it did. It is state, not an effect: the scan is saved while the scanner still
  covers the app, and after a process death nobody is there to take an effect.
- **Suggestions will show here.** `DocumentSuggester` (`domain/suggestions`) is the port for
  proposing a title, description, folder and tags from a document's text. **No build binds one**:
  `SuggestDocumentDetailsUseCase` takes it as nullable, and binding one in `ScannedDocumentModule` is
  all it takes to turn suggestions on.
  - It runs after the text is read: the use case waits for the document's pages
    (`PagesRepository.observeTextStatus`), recognition included.
  - With text recognition off the answer is `SuggestionOutcome.TextRecognitionOff`: the suggester is
    not asked, and the review says why, with the switch and a way to Settings. Turning it on asks
    again.
  - A proposal is never applied by itself; each part is taken with a tap.
  - With no suggester, the review does not mention suggestions.

## The catalogue

- **Room.** The model is in [database.md](database.md). `DocumentsDatabase` (version 5, file
  `scanned_pdfs.db`): a scan is a `documents` row with custody `MANAGED` and origin `SCAN`, a
  `document_activity` row and one `pages` row per page. `documents_fts` is the FTS4 index over name
  and description; its `unicode61` tokenizer ignores case and accents.
  - Paths are relative to the files directory, never `FileProvider` URIs (they depend on the
    authority); `DocumentLocations` builds the URI on request.
  - Column names are the schema; `DocumentMapper` translates them to the domain's `Document`.
  - Rules the tables cannot state are triggers in `DatabaseTriggers.ALL`, used by new databases and
    migrations alike.
  - Migrations are in `DocumentsDatabaseMigrations.kt`, schemas in `library/data/schemas/`. A
    schema change means: bump the version, add the migration, commit the schema JSON. No
    destructive fallback.
- **Home's list.** `HomeViewModel.observeDocuments` takes the library (or what carries the chosen
  tags, `ObserveLibraryUseCase`) and gives it with the filters to `ProcessDocumentsUseCase`:
  **filter**, then **sort**. See [organization.md](organization.md).
- **Recents.** The shelf at the top of Home shows the documents used last
  (`ObserveRecentDocumentsUseCase`), whatever the list is sorted by.
  - **Recent means opened or new.** `document_activity` keeps `last_opened_at` and
    `last_activity_at` (the later of entering the catalogue and last open). It is stored, though
    derivable, because Recents is ordered by it and needs the index.
  - **The viewer reports opens** once each time (`RecordDocumentOpenedUseCase`) and whether the
    file was there (`RecordDocumentAvailabilityUseCase`), since only whoever just tried to read it
    knows. A document whose file was unreachable is shown faded.
  - **Activity is its own table** (`DocumentActivityRepository`) because reading writes it, and it
    also keeps where each document was left ([pdf-viewer.md](pdf-viewer.md#reading-position)). In
    `documents`, each write would re-index the document and make every library list emit.
  - **The bin is not recent; other apps' documents are.** With three documents or fewer the shelf
    would only repeat the list, so it is left out unless it holds another app's document, which
    is in no list and reachable only from the shelf.
  - **The same content appears once.** A PDF opened from a chat arrives at a new location each time
    and is a new linked document. A linked document is left out when the library has one with the
    same hash, or when another linked document with it was used later; the one that stays takes the
    place of those it stands for. The query does this (`ActivityDao.observeRecents`); all rows are
    kept, each with its location and reading position. Two library documents are never merged (a
    second copy is the user's choice), and a never-read linked document has no hash and is listed.
- **Search.** `DocumentSearchViewModel` combines the library with the query (debounced 150 ms) for
  `SearchDocumentsUseCase`. Results are by relevance, each with its page and the words around the
  match when it is in the text. See [Search](#search).
- **Document actions.** Actions, Edit and Delete are destinations with their own keys
  ([navigation.md](navigation.md)), backed by `DocumentActionsViewModel`.
- **Deleting** sends a document to the bin; only the bin deletes for good
  ([database.md](database.md#the-life-of-a-document)).
- **Folders, tags and pages.** Models and ports are in the domain (`FoldersRepository`,
  `TagsRepository`, `PagesRepository`), implemented over Room. Screens: [organization.md](organization.md).
  - **What the user can cause is an answer**: a taken name, a folder moved into itself.
    `FolderChange` and `TagChange` are sealed; the repository checks the rule and changes in one
    transaction. Triggers and indices are the net under it.
  - **Names compare normalized** (`normalizedNameOf`: trimmed, lower case, no accents), so
    "Facturas" and "facturas " are one name among siblings.
  - **Deleting a folder deletes no document.** Its documents (the bin's included) and subfolders go
    to its parent; a subfolder whose name is taken becomes "Name (2)".
  - **A folder cannot contain itself.** The trigger only sees a folder that is its own parent
    (SQLite 3.9 cannot walk ancestors in a trigger); the repository stops longer loops
    (`FolderTree.wouldContainItself`).
- **Export and share.**
  - `DocumentExporter` (FileKit) copies a document where the user chooses, returning
    `Saved` / `Cancelled` / `Failed`.
  - `DocumentSharer` shares the `FileProvider` URI.
  - `CatalogueFileProvider` answers `DISPLAY_NAME` with the catalogue's name, since a plain
    `FileProvider` would show the uuid on disk.

## Documents of other apps

A PDF another app opens in the viewer becomes a `LINKED` document of the catalogue: a reference so
it can be found again in Recents, and nothing else.

- **Its file is never the app's**: never copied, changed or deleted from here. Keeping it is the
  user's choice.
- **Its location is its identity.** `RegisterLinkedDocumentUseCase` reuses the document the
  catalogue has for that URI or adds one, so opening a file ten times is one entry and one reading
  position. The same file through two URIs is two documents.
- **It is not in the library**: no folder, tags, pages or bin, and its text is not read. Triggers
  enforce this; it shows in Recents only.
- **Permission** (`ExternalDocumentAccess`). On registration the app tries to keep read permission
  after the other app is gone. Most apps refuse and a shared file never allows it, so the document
  then opens only while the loan lasts. Whether it was kept is noted, and released when the
  document is forgotten.
- **No more than 50.** They are only listed in Recents, and unbounded they would pile up unseen,
  each holding a permission. Registering one over the limit forgets the least recently used in the
  same transaction. The statement names the custody, so no mistake above it can delete a document
  the app keeps.
- **What it turned out to be** is noted after it opens (`DescribeLinkedDocumentUseCase`): size,
  pages, content hash, password protection. It reads the whole file, so it runs after the document
  is on screen and on every opening, since the file can be replaced. What could not be learnt stays
  as it was. Having hashed the file, it also tells whether the library already keeps that content,
  and the viewer says so ([pdf-viewer.md](pdf-viewer.md#the-external-viewer-d5)).
- **Saving it** (`SaveLinkedToLibraryUseCase`) copies the file and turns the reference into a
  `MANAGED` document with origin `IMPORT`.
  - **It is the same document afterwards**: the row changes in place in one statement, so uuid,
    last opened and reading position stay. The origin is kept in `source_uri`, and it gets the
    pages a kept document has.
  - **The file first, then the catalogue**, as for a scan; if cataloguing fails the copy is removed.
  - **The answer is a result** (`SaveToLibraryOutcome`): saved; not readable (out of reach,
    protected or damaged: the library only keeps what it can show); already in the library (same
    hash); or nothing to save.
  - **A duplicate is asked about, not refused.** The comparison copy is removed and the question is
    a destination (`SaveCopyToLibrary`); yes saves it.
  - **The same location opened after saving is a new linked document**: the reference went with
    the save, and the file is still the other app's.
- **On the shelf** it has no preview (its file is not read to draw one). One unreachable last time
  is faded, and tapping it offers *Remove from Recents* instead of an error
  (`ForgetLinkedDocumentUseCase`); only the reference goes.

## Search

`SearchIndex` is the port: text in, matching documents out, best first. Its implementation
`Fts4SearchIndex` (bound in `ScannedDocumentModule.kt`) uses SQLite's FTS4, which Room supports and
every device has. FTS5, for substring and CJK search, would be another implementation and one line
of DI.

- **Two indexes.** `documents_fts` covers title, original name, suggested title, description and
  the PDF's author, subject and keywords. `page_texts_fts` covers page text. Neither holds text of
  its own: they point at `documents` and `page_texts`, and triggers keep them in step.
- **`unicode61`** ignores case and accents in the index and the query, so "cancion" finds
  "Canción".
- **What the user types is text, never syntax** (`Fts4Query`). FTS4 gives meaning to quotes, `*`,
  `-`, `:`, `OR`, `NOT` and `NEAR`. The query is cut into words as the tokenizer cuts a document,
  lowered, limited to eight; each becomes a prefix and they are joined with spaces, which requires
  all of them. Never `AND`: in Android's SQLite query syntax that is a word to look for.
  - Accents stay in the terms: SQLite passes each term through the tokenizer, and also removing
    them in Kotlin would apply other rules and break words where the two disagree.
- **Relevance** (`Bm25`). FTS4 has no ranking function, so Kotlin computes Okapi BM25 from
  `matchinfo(…, 'pcnalx')` with column weights (title 10, original name 8, suggested title 6,
  description 4, PDF metadata 2, page text 1). A document scores its own row plus half its best
  page; ties go to the most recently used.
- **Passages.** A page match comes with `snippet()`: about twelve words around it and which matched.
  The marks around a match are control characters removed in `MarkedFragment`, so a document's own
  brackets and asterisks stay text.
- **Only the library.** Both queries use the `library_documents` view, which excludes the bin and
  other apps' documents.
- **Limits.** FTS4 finds whole words and their beginnings, not text inside a word, and does not
  split Chinese, Japanese or Korean into words.

## Reading the text of pages

The viewer reads the text of the pages on screen and forgets it. For search, the text of every
library page is read in the background and kept (`IndexDocumentTextUseCase`).

- **A queue behind a port.** `DocumentIndexQueue` is implemented by WorkManager, one unique work per
  document (`index/<uuid>`, `KEEP`), so reading outlives the screen and the process.
  `IndexDocumentWorker` only gives the use case somewhere to run.
- **WorkManager may not start, and that never stops the app.** Its startup initializer is removed in
  the manifest and `App` is its `Configuration.Provider`, so it is built on first use. Some devices
  report an Android version whose system lacks a method WorkManager calls
  (`JobScheduler.forNamespace`, API 34), which would end the process at launch if the initializer
  started it. The gateway answers `null` for such a device and the queue reads the document in the
  app, one at a time, for the life of the process (the start finds what is left). The daily upkeep
  (`WorkManagerLibraryMaintenance`) does the same at each start.
- **When a document is queued.** When a scan is saved, when another app's document is saved, and at
  app start (`ResumeTextIndexingUseCase`, from `App`) for every library document with a page still
  pending. The start covers a just-migrated library, work that never ran, and a save that could not
  queue; saving never fails because of the queue.
- **Page by page, each in one transaction** (`PageDao`): text, origin and state are written
  together, so a process death leaves what was read and the rest pending. Text is removed and
  added, never replaced: replacing a row does not run the triggers that keep `page_texts_fts` in
  step, and the old words would still be found.
- **The document's own text first**, from the PDF's text layer with the viewer's
  `PlatformPageContentProvider`. A page with text becomes `EXTRACTED`, origin `EMBEDDED`, engine
  `platform`.
- **Recognition only where needed, and only if asked.** A page without text of its own, or any page
  below API 35, goes to the recognition engine when its document has `ocr_enabled`; otherwise it
  becomes `OCR_DISABLED` and waits. Recognition is slow and uses battery, so a page whose text the
  PDF gives is never sent.
  - A recognized page is `EXTRACTED` with origin `RECOGNIZED`, confidence, engine (`mlkit-latin`)
    and language. An image with no words is `NO_TEXT` and not asked again.
  - **Each word's position is stored** (`page_layouts`, `PageLayoutCodec`): a PDF's own text can be
    read again from the PDF, but recognized text would cost another recognition. The viewer selects
    on a scanned page from what is stored.
  - Whether recognition is wanted is asked page by page and after each pass, since it can be turned
    on from the document's actions mid-reading.
- **Turning it on and off** (`SetDocumentTextRecognitionUseCase`). On: waiting pages become pending
  and the document is queued. Off: in one transaction recognized text and layouts are deleted and
  those pages wait again, so they stop being found by content. The document's own text is untouched.
- **New documents** get the setting *Recognize text in new documents* (Settings → Scanning, off
  until chosen) when a scan
  or another app's document is saved.
- **Failures are counted per page.** A failed page is retried at once up to three times, then
  `FAILED`; the others are still read. Failed pages become pending at app start, since the cause
  (a file out of reach) may be over. Cancellation is not a failure.
- **A newer extractor reads again.** Each page keeps the version that read it
  (`IndexDocumentTextUseCase.EXTRACTOR_VERSION`); raising it makes older pages pending at the next
  start, keeping their text until reread.
- **Not read:** documents in the bin and of other apps. A document deleted mid-reading ends the
  reading without error.
- **Pages never counted.** A document migrated without a page count has no page rows; the reader
  counts them (`DocumentStorage.pageCount`) and creates them first.
- **Not done yet.** Nothing on screen shows reading progress (`PagesRepository.observeTextStatus`
  exists for it), and the choice is not offered when saving, only as the setting and in the
  document's actions.

## Not yet verified on a device

The process-death paths are covered by `ActivityResultHostImplTest`, not by a real kill:
1. Start a scan, run `adb shell am kill com.bobbyesp.docucraft.debug`, finish the scan: the document
   must appear saved.
2. The same with "Don't keep activities" on.
3. Kill before the scanner shows: the spinner must stop on its own.
