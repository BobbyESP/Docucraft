<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Organizing the library

Folders, tags and favorites: how the user arranges the documents the app keeps, and where each shows
on screen. Code: `:feature:organization` (`presentation/folders`, `presentation/tags`), and the
badges and forms other screens share in `:library:ui` (`organization/`).

Only documents the app keeps are organized. A document of another app is in no folder, carries no
tag and is never a favorite; the catalogue's triggers refuse it.

## The three ways to organize

| | A document has | Kept in | Shown |
|---|---|---|---|
| **Folder** | one at most; none is the root | `documents.folder_id` | Home's pinned folders, the folder's own screen |
| **Tag** | any number | `document_tags` | Home's tag sections, the filter chips, the document's tags sheet |
| **Favorite** | yes or no | `documents.is_favorite` | a star after the name, the Favorites filter |

- **Folders nest.** The catalogue sets no depth limit; the app does: `FolderDepth.MAX` (a folder in
  the root is at depth 1). It is checked where a folder is created (`SaveFolderUseCase`) and moved
  (`MoveToFolderViewModel`), so the path to a folder always fits in an app bar.
- **A name is unique among sibling folders and among all tags**, regardless of case, accents or
  spaces (`normalizedNameOf`). A taken name is an answer (`FolderChange.NameTaken`,
  `TagChange.NameTaken`) shown under the field.
- **Deleting a folder deletes no document**; what it holds goes to its parent. Deleting it with its
  contents is a switch in the confirmation, off each time: the folders inside go too and their
  documents go to the bin. Deleting a tag only takes it off its documents.
- **Each folder remembers the order of its documents** (`FoldersRepository.setSort`). The root has
  no folder to remember it in, so its order lasts while its screen does.

## Colors and icons

A folder has a color and an icon, a tag a color. Both are **keys of a closed palette** (`LabelColor`,
`FolderIcon`, in `domain/model/Palette.kt`), never a color value or a drawable id: a value would not
follow the theme, and an id changes between builds. An unknown key reads as the default.

How a key looks is decided at the edge, in `LabelPalette.kt`. A `LabelColor` is a hue whose tones are
computed for the current theme (`LabelColor.tones()`): the hue is harmonized towards the theme's
primary, and the accent, container and content tones are the primary roles a scheme of that color
has in the app's palette style (`accentRoles`), computed without building the scheme. So no color
clashes with a wallpaper color it was not chosen next to, and a folder's badge matches the screen it
opens (`LabelColorTheme`). Each tone is computed once per color, primary, darkness and style. No
color at all is the theme's primary.

## Home

Top to bottom (`HomeContent.kt`):
1. **Recents**, the documents used last.
2. **Folders**: the pinned ones, as cards in their own colors. Always present, since it is also the
   way into the folders; with none pinned it says what goes there.
3. **A section for each tag the user chose**, in their order (`tags.home_position`), as a shelf of
   first pages. *See all* narrows the list below to that tag and scrolls to it.
4. **Every document**, sorted and narrowed by the chips above: favorites, and any number of tags,
   all of which a document must carry.

`ObserveHomeSectionsUseCase` gives 2 and 3. For 4, `ObserveLibraryUseCase` asks the catalogue for the
whole library or what carries the chosen tags, and `ProcessDocumentsUseCase` filters favorites and
sorts. `HomeViewModel` observes the two apart, so a renamed tag does not re-sort the documents.

## Destinations

All are keys of the one back stack (`navigation/OrganizationKeys.kt`); see
[navigation.md](navigation.md#modal-destinations) for why a sheet is a destination.

| Key | What it is | Container |
|---|---|---|
| `FolderContents(folderUuid?)` | A folder's folders and documents; the root for no uuid | A list pane, like Home and search |
| `FolderEditor(folderUuid?, parentUuid?)` | Name, color and icon, of a folder or of a new one | Sheet or dialog |
| `FolderActions(folderUuid)` | Edit, pin, move, delete | Sheet |
| `DeleteFolder(folderUuid)` | Confirmation | Sheet or dialog |
| `MoveToFolder(documentUuid?, folderUuid?)` | Choosing where a document or a folder goes | Sheet or dialog |
| `DocumentTags(documentUuid)` | The tags of a document | Sheet or dialog |
| `ManageTags` | Every tag, and which have a section in Home | A screen |
| `TagEditor(tagUuid?)` | Name and color, of a tag or of a new one | Sheet or dialog |
| `DeleteTag(tagUuid)` | Confirmation | Sheet or dialog |
| `Bin` | What was deleted, each with the days it has left | A screen |
| `BinDocumentActions(documentUuid)` | Restore, or delete for good | Sheet |
| `DeleteForever(documentUuid)`, `EmptyBin` | Confirmations | Sheet or dialog |

The bin's keys are in `navigation/BinKeys.kt`. It is entered from the root of the folders, not from
a folder: the bin is the library's. What it does is in
[database.md](database.md#the-life-of-a-document).

- **A form is written once.** `OverlayForm` (`core/navigation/overlay/`) renders the same heading,
  body and two buttons as a sheet or a dialog, as `LocalOverlayContext` says.
- **What is typed lives in the composition** (`rememberSaveable`), not the ViewModel, which holds
  what the folder is and what was wrong with the last name.
- **Choosing a destination folder walks the folders inside its sheet.** Where the user is looking is
  that sheet's state, in its `SavedStateHandle`, discarded with it: not a place to return to, so not
  a destination and not a nested `NavDisplay`.
- **An overlay closes itself when what it acts on is gone**, by observing it, as the document's
  actions do. Deleting a folder takes this path: the confirmation, the actions sheet under it and
  the folder's screen each notice.
- **One ViewModel per navigation entry.** `FolderViewModel` serves the editor, actions and
  confirmation of one folder; `TagsViewModel` the tags screen and what acts on one tag.

## What every list of documents shares

A document whose file is missing is listed like any other, faded and saying *File not found*
(`ObserveNotFoundDocumentsUseCase` tells the lists which). It is never left out: only the user
deletes a document.

Home and a folder's screen are built from the same parts. The large app bar that frosts as the list
scrolls under it and the section header are shared by every list screen, settings included
(`core/designsystem/components/ScreenChrome.kt`); the sort menu is theirs alone
(`presentation/components/list/DocumentListChrome.kt`). A screen that floats a button over its list
lifts it with a blur halo, as Home does ([architecture.md](architecture.md#blur)).

## Not done yet

- Tags are not shown on a document's row: a row would need its tags, and the lists do not ask for
  them.
- A folder is moved to where a folder can be created, without checking how deep the folders inside it
  go, so moving one that holds others can leave some deeper than `FolderDepth.MAX`. They are still
  reached and shown; only no folder can be created in them.
