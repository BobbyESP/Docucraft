<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Navigation

One back stack, one Navigation 3 `NavDisplay`, typed keys. Code: `:core:navigation`, the shell's
`navigation/` package in `:app`, and each feature's section.

## The model

- **The back stack is the whole navigation state.** `DocucraftApp` creates it with
  `rememberNavBackStack(Home)`, so it survives configuration changes and process death. Back,
  including the predictive gesture, pops it. The stack is only exited at Home, so back closes an open
  document, never the app.
- **Features contribute entries and say where they want to go. Nothing else.**
  - Each registers only its own destinations in a section (`homeSection`, `foldersSection`,
    `pdfViewerSection`…). `docucraftSections`, in `DocucraftApp.kt`, is the one list, which the
    store's screenshots use too.
  - Each receives a `Navigator`, so the shell does not know the graph's shape and a feature never
    reads or reorders the stack.
- **`Navigator`**:

  | Call | Does |
  |---|---|
  | `goTo(key)` | pushes `key`; asking for the destination already on top does nothing |
  | `goBack()` | pops, but never the root |
  | `goBackWhile(predicate)` | pops a related group at once, such as a confirmation and the menu that opened it |
  | `removeDestination(key)` | removes `key` wherever it sits |

  `removeDestination` is for a destination whose subject is gone, such as a deleted document. It is
  not `goBack`: wanting yourself gone is not popping whatever is on top, which need not be you.
- **Keys are `@Serializable`.** A key one feature opens another by lives in `:core:navigation`
  (`core/navigation/keys/`), so the opener depends on keys, never on screens; a key only its own
  feature opens stays there:

  | Module | File | Keys | Destination of |
  |---|---|---|---|
  | `:feature:home` | `HomeKey.kt` | `Home` | `:feature:home` |
  | `:core:navigation` | `DocumentSearchKey.kt` | `DocumentSearch` | `:feature:search` |
  | `:core:navigation` | `DocumentActionKeys.kt` | `DocumentActions`, `EditDocument`, `DeleteDocument` | `:feature:document-actions` |
  | `:core:navigation` | `ScanKeys.kt` | `ReviewScan` | `:feature:scan` |
  | `:core:navigation` | `BinKeys.kt` | `Bin`, `BinDocumentActions`, `DeleteForever`, `EmptyBin` | `:feature:bin` |
  | `:core:navigation` | `OrganizationKeys.kt` | `FolderContents`, `FolderEditor`, `FolderActions`, `DeleteFolder`, `MoveToFolder`, `DocumentTags`, `ManageTags`, `TagEditor`, `DeleteTag` | `:feature:organization` |
  | `:core:navigation` | `PdfViewerKey.kt` | `PdfViewer`, the one other features open | `:feature:pdfviewer` |
  | `:feature:pdfviewer` | `ViewerKeys.kt` | `ExternalPdfViewer`, `PdfDocumentDetails`, `SaveCopyToLibrary`, `GoToPage` | `:feature:pdfviewer` |
  | `:core:navigation` | `SettingsKeys.kt` | `Settings`, `AppearanceSettings`, `ScanningSettings`, `DocumentViewerSettings`, `AboutSettings` | `:feature:settings` |

  A key in `:core:navigation` carries uuids at most. The viewer's own keys carry its
  `ViewerDocumentRef`, which is why they stay with it.

- **`DocucraftNavDisplay` is the one way a back stack is rendered**, by `MainActivity`'s shell and by
  `PdfViewerActivity`'s own stack, so every entry keeps its state and moves the same way.
  - Its decorators are in the order the library requires, saveable state before the
    `ViewModelStore`, which gives each entry its own ViewModel and a working `SavedStateHandle`.
  - Transitions come from `NavigationMotion` alone; no screen contributes its own. The exceptions
    are defined there too, and a destination asks for one in its `entry` metadata:
    - A destination reached *through* an element shared with the previous one (search, through
      Home's search bar) cross-fades (`SharedElementMotion`) and lets the element carry the motion;
      the door slide would drag the screen sideways while the bar grows upwards.
    - A destination that interrupts rather than follows (the review of a scan, as the scanner
      closes) rises from the bottom edge over what is there and sinks back (`RisingMotion`). What is
      under it stays still: a step forward slides in from the side, and this is not one.
  - Shared elements animate in one `SharedTransitionLayout` around the display. Its scope reaches
    destinations through `LocalNavSharedTransitionScope`, not `NavDisplay`'s own parameter: given
    the scope, the display wraps *every* entry in a shared element and each destination glides
    between panes. `Modifier.sharedBoundsAcrossDestinations(key, shape)` marks the two ends.

## Scenes: how the stack is laid out

Two scene strategies, tried in order; the first to claim the top entry wins.

1. **`OverlaySceneStrategy`** shows entries marked `OverlaySceneStrategy.overlay(...)` above the
   rest.
   - The container depends on the window: a modal bottom sheet below medium width, a dialog from
     medium width. `OverlayPreference.AlwaysSheet` keeps lists of choices, such as document actions,
     in a sheet on any window.
   - The destination learns where it landed from `LocalOverlayContext`, including whether there is
     height to stack a header above its content.
2. **The list-detail strategy** (Material 3 adaptive) puts Home and the viewer side by side when two
   panes fit (expanded width). `sharingTheWindow()` wraps it so its destinations learn they share
   the window through `LocalPaneContext`. Whatever it declines falls through to a single pane.
   - Search is a list pane too: on a wide window it takes Home's place beside the open document, so
     a result opens next to the results. Opening one replaces an open viewer but keeps search on the
     stack, so back returns to the results.
   - Picking a document from the list replaces the open one instead of stacking, or every document
     looked at beside the list would become a back step, and narrowing the window would leave a
     trail to back out of.

**Told, not measured.** A destination never reads the window size or the device orientation: in a
pane or a dialog those answers do not match the room it has, and a screen that measured for itself
acted as if it had the whole window. Layout that depends on a component's own constraints is fine:
the viewer's toolbar decides from its own width whether the zoom buttons fit.

## Modal destinations

**A sheet or dialog the user can reach, leave and come back to is a destination.** It goes on the
same back stack with its own key, and back means "take it off". Document actions, edit, delete, a
document's details and *Go to page* all work this way. Rotating shows the same entry in another
container; form state lives in the entry's `rememberSaveable`, so it survives.

**Never put a `NavDisplay` inside a sheet or a dialog for such a destination.** This decision
caused more bugs than any other here. A hand-made stack in a `NavDisplay` inside a
`ModalBottomSheet` had four consequences:
1. **The inner stack was not saved**, so a process death lost it.
2. **Its pages were not entries**, so they had neither a ViewModel nor saved state of their own.
3. **Sheet-or-dialog and the inner stack were two states that had to agree**, and on rotation they
   did not.
4. **Back had two meanings.** Every press had to be routed by hand, inner or outer, and that code
   closed the wrong thing or needed two presses.

A nested display is still right for a self-contained flow discarded whole, such as an onboarding
inside a dialog. Floating windows (`ModalBottomSheet`, `Dialog`) have their own back dispatcher, so
back stays inside them.

**One destination, one entry, one back press.** If some UI wants a second meaning for back on the
same surface, a destination is missing or a state is one too many. The sheet container follows this:
it only allows `Hidden` and `Expanded`, since a half-expanded state would make the first back press
collapse the sheet instead of closing it.

**What is behind a modal destination goes out of focus.** While the top entry is shown as an
overlay, `DocucraftNavDisplay` blurs everything it draws (`outOfFocusBehindOverlay`, in
`NavigationMotion.kt`) on the motion scheme's effects spec, with the scrim the container draws. It
asks the entry the same question the overlay strategy does (`OverlaySceneStrategy.isShownAsOverlay`),
so they cannot disagree. The overlay is its own window, so the blur never reaches it and the sheet
or dialog stays solid. A dialog a screen shows for itself, outside the back stack, blurs nothing:
the appearance dialog is meant to show the theme changing behind it.

**Transient popups anchored to content are not destinations.** A dropdown menu, or a link's preview
in the viewer, is attached to a spot on the screen, closed by any scroll and meaningless after a
process death. It is UI state, not an entry.

## Requests from outside the UI

Something outside the composition may need a destination: a home-screen widget asking for a scan, a
document or the search, or *Go to page* returning a page to the viewer. It leaves a **standing
request** in a small holder in Koin (`ScanRequestBus`, `LibraryEntryRequests`,
`ViewerPageRequests`); whoever can act on it takes it when it can and consumes it. A one-shot event
is lost when nobody listens at that moment, then fires later out of context.

What `MainActivity` can be asked by an Intent is in `EntryIntents.kt` (`:feature:widgets`):
`ACTION_SCAN_DOCUMENT`, `ACTION_OPEN_DOCUMENT` (with the document's uuid) and
`ACTION_SEARCH_DOCUMENTS`. The activity turns each into its standing request and does nothing else,
because it runs before anything is composed.

`LibraryEntryRequestNavigation`, hosted by the shell next to `ScanRequestNavigation`, acts on a
document or the search:
- It goes there **as if from Home**, leaving whatever was open, so back from a document a widget
  opened leads to the library, not to a screen left open days before.
- It looks the document up first. A widget shows the library as of its last drawing, so the document
  may be in the bin since: the user is told so and nothing opens.

## The external viewer's stack

`PdfViewerActivity` opens other apps' PDFs in its own task, with its own back stack rooted at
`ExternalPdfViewer`, rendered by the same `DocucraftNavDisplay` so details and *Go to page* work as
in the app. See [pdf-viewer.md](pdf-viewer.md#the-external-viewer-d5).
