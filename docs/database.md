<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# The catalogue's database

What the catalogue keeps, the rules it keeps it by, and the decisions behind both. Code:
`:library:data`, `db/` (Room), with the repositories of `repository/` over it and their ports in
`:library:domain`, `repository/`.

How documents get in is in [scanning.md](scanning.md); how they are arranged, in
[organization.md](organization.md). This document is the model underneath.

## What is kept

`DocumentsDatabase`, version 5, in the file `scanned_pdfs.db`.

| Table | One row is | Why it is a table of its own |
|---|---|---|
| `documents` | A document: one the app keeps (`MANAGED`), or one of another app that is only referred to (`LINKED`) | Recents lists both together, so they are one table with a `custody` column |
| `document_activity` | What was done with a document: last opened, last activity, reading position, whether its file was reached | Reading writes it; in `documents`, each write would re-index the document and make every list emit |
| `pages` | A page, and how far reading its text has got | Text is kept by page, so a search says which page matched |
| `page_texts` | The text of a page and where it came from: embedded or recognized | Recognized text is deleted on its own when recognition is turned off |
| `page_layouts` | Where each word of a recognized page is | Only the viewer wants it, and it is large |
| `folders` | A folder, with its parent | |
| `tags`, `document_tags` | A tag, and which documents carry it | |
| `documents_fts`, `page_texts_fts` | The two full-text indexes: what a document is called and described as, and what its pages say | FTS4, kept in step by Room's triggers |

Two views: `library_documents`, the kept documents not in the bin (what almost every query wants),
and `document_text_status`, how much of a document has been read.

- **A document is identified by its `uuid`**, which never changes (rename, move, save into the
  library). The row id never leaves the data layer.
- **A document's file is named after its uuid** (`documents/<uuid>.pdf`) and the catalogue keeps the
  path relative to the files directory. Renaming touches no file, documents never collide, and the
  database stays valid if the app's directory moves. Older documents are in `scans/pdf/` under their
  scan's name and are never moved; the catalogue says where each is.
- **Column names are the schema**, in English (DB11). `DocumentMapper` translates them to the
  domain's `Document`.

## Rules the tables cannot state

Room cannot declare a `CHECK`. What a table cannot say is a trigger in `DatabaseTriggers.ALL`, the
one list a new database is created with and a migration applies, so the two cannot differ
(`SchemaParityTest` compares them).

- **Custody is consistent.** A `MANAGED` document has a file path and an origin; a `LINKED` one has
  a URI and none of what only a kept document has: a folder, a favorite mark, text recognition, a
  date in the bin.
- **Only kept documents are tagged.**
- **A folder is not its own parent**, and no two siblings share a normalized name, the root's
  included (a `UNIQUE` index does not see two `NULL` parents as equal).

A longer loop of folders is stopped by the repository (`FolderTree.wouldContainItself`), since SQLite
3.9 cannot walk ancestors inside a trigger. In general, **a repository checks a rule and makes the
change in one transaction**, so what the user can cause comes back as an answer (`FolderChange`,
`TagChange`) and the triggers stay as the net under it.

The SQL must run on API 24's SQLite (3.9): no UPSERT, window functions, generated columns or
`RENAME COLUMN`, and no `WITH` inside a trigger.

## Migrations

In `DocumentsDatabaseMigrations.kt`, with schemas exported to `library/data/schemas/`. A schema
change means: bump the version, add the migration, commit the new schema JSON.

- **No destructive fallback.** A version nothing migrates from stops the app rather than emptying
  the library.
- **A migration keeps every document and its uuid** and only touches the database; it moves no file.
- `Migration4To5` is written against `SchemaVersion5`, a frozen copy of the schema as it was, so a
  later change to an entity cannot change what an old migration does.

## The life of a document

```
            save, import                    delete                    30 days, or emptied
  nothing ───────────────▶ in the library ─────────▶ in the bin ─────────────────────▶ gone
                                  ▲                       │
                                  └────── restore ────────┘
```

**Deleting is reversible, and nothing automatic deletes a document of the library.** Every process
below answers to that invariant.

- **Deleting sends a document to the bin** (`MoveDocumentToBinUseCase`): `trashed_at` is written and
  nothing else, so it keeps its folder, tags, text and file. It leaves the library, search and
  Recents, which read `library_documents` or filter on `trashed_at`, and to everything but the bin
  it is observed as gone, which closes a viewer or sheet open on it.
- **Restoring returns it to the folder it is in** (`RestoreDocumentUseCase`). That folder exists:
  deleting a folder moves what it holds, the bin's documents too, to the folder above. Its text is
  not read while in the bin, so restoring queues it to be read.
- **Only the bin deletes for good** (`DeleteFromBinUseCase`): by the user (one document or the whole
  bin) or after `BinRetention.DAYS`. The statement (`DocumentDao.deleteFromBin`) only matches a
  document in the bin, so no caller can reach the library with it. The row goes first, cascading to
  its activity, pages, text, layouts and tags; then the file, unless another document still has it;
  then the previews. A row pointing at a missing file is visible to the user, while a file nothing
  points at is not, and the reconciliation removes it.
- **Deleting a folder deletes no document** (`FoldersRepository.delete`). Deleting it *with what it
  holds* is another action, asked for by name (`deleteWithContents`): the folders under it go and
  their documents go to the bin, not further.

## Upkeep

`LibraryMaintenance` schedules it daily with WorkManager (`WorkManagerLibraryMaintenance`), from
`App`. Each run does two independent things:
- **Purge**: `PurgeExpiredBinUseCase` deletes what has been in the bin for 30 days.
- **Reconciliation**: `ReconcileStorageUseCase` checks that the catalogue and the files agree.
  - *A file no document refers to* is removed once a day old: a document is stored before it is
    catalogued, so a new file without a row may be one being saved now, while an old one is left by
    a save that died half way or a delete that could not remove its file.
  - *A document whose file is missing* is marked `NOT_FOUND` in `document_activity` and shown so in
    every list, faded and saying *File not found*. It is never deleted: the file may return (the app
    restored on another device without its files), and deleting is the user's decision.
  - *A document marked so whose file is back* is available again.
  - Previews are a cache: those of documents that are gone are dropped.

## Decisions

Named in code comments as DB1 to DB11, apart from the viewer's D1 to D5.

| | Decision | Where it shows |
|---|---|---|
| DB1 | Search is Room's FTS4 with the `unicode61` tokenizer, behind a port (`SearchIndex`). It works from API 24 with no new dependency and ignores case and accents | [scanning.md](scanning.md#search) |
| DB2 | A document is recent because it was opened or because it is new: `last_activity_at` is the later of the two, stored so it can be indexed | [scanning.md](scanning.md#the-catalogue) |
| DB3 | Of another app's PDF only the reference is kept. *Save to Docucraft* keeps it for good | [scanning.md](scanning.md#documents-of-other-apps) |
| DB4 | Folders nest. The database sets no depth limit; the app does (`FolderDepth.MAX`) | [organization.md](organization.md#the-three-ways-to-organize) |
| DB5 | Home has a fixed section of pinned folders and a section for each tag the user chooses and orders (`tags.home_position`) | [organization.md](organization.md#home) |
| DB6 | The bin keeps a document for 30 days | [The life of a document](#the-life-of-a-document) |
| DB7 | Text recognition is the user's choice, per document (`documents.ocr_enabled`), changeable later | [scanning.md](scanning.md#reading-the-text-of-pages) |
| DB8 | The database and the documents are left out of Auto Backup: a database restored without its files would be a library of unopenable documents | `backup_rules.xml`, `data_extraction_rules.xml` |
| DB9 | No more than 50 documents of other apps are remembered | [scanning.md](scanning.md#documents-of-other-apps) |
| DB10 | Where a document was left is remembered per document, and the user can turn it off | [pdf-viewer.md](pdf-viewer.md#reading-position) |
| DB11 | Everything in the code is in English: tables, columns, classes, comments and the triggers' messages | this document |

## Not done

- **Importing a PDF from inside the app.** Apart from scanning, the only way in is *Save to
  Docucraft* in the external viewer.
- **A PDF's own metadata** (author, subject, keywords, creation date): the columns exist and search
  weighs them, but nothing fills them because the platform does not expose them.
- **The document's own date**, as opposed to when it was saved: the column exists with no way to set
  it or filter by it.
- **Exporting and importing the library**, which is what DB8 leaves backups to.
