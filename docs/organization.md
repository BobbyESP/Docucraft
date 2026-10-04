<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Organizing the library

Folders, tags and favorites: how the user arranges the documents the app keeps, and where each of
those shows on screen. Code: `app/.../feature/docscanner`, in `presentation/screens/folders`,
`presentation/screens/tags` and `presentation/components/organization`.

Only documents the app keeps are organized. A document of another app is in no folder, carries no
tag and is never a favorite; the catalogue's triggers refuse it.

## The three ways to organize

| | A document has | Kept in | Shown |
|---|---|---|---|
| **Folder** | one at most; none is the root | `documents.folder_id` | Home's pinned folders, the folder's own screen |
| **Tag** | any number | `document_tags` | Home's tag sections, the filter chips, the document's tags sheet |
| **Favorite** | yes or no | `documents.is_favorite` | a star after the name, the Favorites filter |

- **Folders nest**, and the catalogue sets no limit to how deep. The app does: `FolderDepth.MAX`
  (a folder in the root is at depth 1). It is checked where a folder is created
  (`SaveFolderUseCase`) and where one is moved (`MoveToFolderViewModel`), so the path to a folder
  always fits in an app bar.
- **A name is unique among siblings** for folders, and among all tags, without regard to case,
  accents or spaces (`normalizedNameOf`). A name that is taken is an answer, `FolderChange.NameTaken`
  or `TagChange.NameTaken`, said under the field the user typed it in.
- **Deleting a folder deletes no document.** What it holds goes to the folder it was in. Deleting
  it together with what it holds is a switch in the confirmation, off each time: the folders inside
  it go too, and their documents go to the bin. Deleting a tag only takes it off its documents.
- **Each folder remembers the order of its documents** (`FoldersRepository.setSort`). The root has
  no folder to remember it in, so its order lasts while its screen does.

## Colors and icons

A folder has a color and an icon, and a tag has a color. Both are **keys of a closed palette**
(`LabelColor`, `FolderIcon`, in `domain/model/Palette.kt`), never a color value or a drawable id: a
value would not follow the theme, and an id changes from one build to the next. A key the app does
not know reads as the default.

What a key looks like is decided at the edge, in `LabelPalette.kt`. A `LabelColor` is a hue; its
tones are computed for the current theme (`LabelColor.tones()`): the hue is harmonized towards the
theme's primary, and the accent, container and content tones are picked as Material picks a
container's, lighter in light and darker in dark. No color therefore clashes with a wallpaper color
it was not chosen next to. No color at all is the theme's primary.

## Home

Top to bottom (`HomeContent.kt`):

1. **Recents**, the documents used last.
2. **Folders**: the pinned ones, as cards in their own colors. The section is always there, since it
   is also the way into the folders; with none pinned it says what goes there.
3. **A section for each tag the user chose**, in the order they chose
   (`tags.home_position`), with the documents that carry it as a shelf of first pages. *See all*
   narrows the list below to that tag and scrolls to it.
4. **Every document**, sorted, and narrowed down by the chips above it: favorites, and any number
   of tags, which a document must carry all of.

`ObserveHomeSectionsUseCase` gives 2 and 3. For 4, `ObserveLibraryUseCase` asks the catalogue for
the whole library or for what carries the chosen tags, and `ProcessDocumentsUseCase` filters the
favorites and sorts. The two are observed apart in `HomeViewModel`, so a renamed tag does not sort
the documents again.

## Destinations

All of them are keys of the one back stack (`navigation/OrganizationKeys.kt`); see
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

The bin's keys are in `navigation/BinKeys.kt`. It is entered from the root of the folders, where
the library's folders start, and not from a folder: the bin is the library's. What it does is in
[database.md](database.md#the-life-of-a-document).

- **A form is written once.** `OverlayForm` (`core/presentation/components/overlay/`) renders the same heading, body and two buttons as a
  sheet or as a dialog, as `LocalOverlayContext` says.
- **What is typed lives in the composition** (`rememberSaveable`), not in the ViewModel: it is not
  what the folder is. The ViewModel holds what the folder is, and what was wrong with the last
  name.
- **Choosing a destination folder walks the folders inside its sheet.** Where the user is looking
  is that sheet's state, kept in its `SavedStateHandle` and discarded with it: not a place to come
  back to, so not a destination, and not a nested `NavDisplay` either.
- **An overlay closes itself when what it acts on is gone**, by observing it, as the document's
  actions do. Deleting a folder is that same path: the confirmation, the actions sheet under it and
  the folder's own screen each notice.
- **One ViewModel per navigation entry.** `FolderViewModel` serves the editor, the actions and the
  confirmation of one folder; `TagsViewModel` serves the tags screen and what acts on one tag.

## What every list of documents shares

A document whose file is not there is listed like any other, faded and saying *File not found*
(`ObserveNotFoundDocumentsUseCase` gives the lists which ones). It is never left out: only the user
deletes a document.

Home and a folder's screen are built from the same parts
(`presentation/components/list/DocumentListChrome.kt`): the large app bar that frosts once the list
scrolls under it, the section header, and the sort menu. A screen that floats a button over its list
lifts it with a blur halo, as Home does; see [architecture.md](architecture.md#blur).

## Not done yet

- Tags are not shown on a document's row in a list. A row would need its document's tags, and the
  lists do not ask for them.
- A folder is moved to where a folder can be created, without looking at how deep the folders
  inside it go: moving a folder that holds others can leave some deeper than `FolderDepth.MAX`.
  They are still reached and shown; only no folder can be created in them.
