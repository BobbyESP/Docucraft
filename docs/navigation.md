<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Navigation

One back stack, one Navigation 3 `NavDisplay`, typed keys. Code:
`app/.../core/presentation/navigation/` and each feature's `navigation/` package.

## The model

- **The back stack is the whole navigation state.**
  - `DocucraftApp` creates it with `rememberNavBackStack(Home)`, so it survives configuration
    changes and process death.
  - Back, including the predictive gesture, pops it.
  - The stack is only ever exited at Home, so back closes an open document, never the app.
- **Features contribute entries and say where they want to go. Nothing else.**
  - Each registers its destinations in `entryProvider` (`homeSection`, `pdfViewerSection`,
    `settingsSection`).
  - Each receives a `Navigator`. The shell does not know the shape of the graph, and a feature
    never reads or reorders the stack.
- **`Navigator`**:

  | Call | Does |
  |---|---|
  | `goTo(key)` | pushes `key`; asking for the destination already on top does nothing |
  | `goBack()` | pops, but never the root |
  | `goBackWhile(predicate)` | pops a related group at once, such as a confirmation and the menu that opened it |
  | `removeDestination(key)` | removes `key` wherever it sits |

  `removeDestination` is for a destination whose subject is gone, such as a deleted document. It
  is not `goBack`: wanting yourself gone is not the same as popping whatever is on top, which need
  not be you. Confusing the two once closed the wrong screen.
- **Keys are `@Serializable` and owned by their feature**:

  | File | Keys |
  |---|---|
  | `feature/docscanner/navigation/HomeKey.kt` | `Home` |
  | `feature/docscanner/navigation/DocumentSearchKey.kt` | `DocumentSearch` |
  | `feature/docscanner/navigation/DocumentActionKeys.kt` | `DocumentActions`, `EditDocument`, `DeleteDocument`, `ReviewScan` |
  | `feature/docscanner/navigation/BinKeys.kt` | `Bin`, `BinDocumentActions`, `DeleteForever`, `EmptyBin` |
  | `feature/docscanner/navigation/OrganizationKeys.kt` | `FolderContents`, `FolderEditor`, `FolderActions`, `DeleteFolder`, `MoveToFolder`, `DocumentTags`, `ManageTags`, `TagEditor`, `DeleteTag` |
  | `feature/pdfviewer/navigation/PdfViewerKey.kt` | `PdfViewer`, `ExternalPdfViewer`, `PdfDocumentDetails`, `GoToPage` |
  | `core/.../preferences/navigation/SettingsKeys.kt` | `Settings`, `AppearanceSettings`, `DocumentViewerSettings`, `AboutSettings` |

- **`DocucraftNavDisplay` is the one way a back stack is rendered.** Both `MainActivity`'s shell
  and `PdfViewerActivity`'s own stack use it, so every entry keeps its state and moves the same
  way.
  - Its decorators come in the order the library requires, saveable state before the
    `ViewModelStore`. That is what gives each entry its own ViewModel, and a `SavedStateHandle`
    that works.
  - Transitions come from `NavigationMotion` alone. No screen contributes its own.
  - The exceptions are also defined there, and a destination asks for one in its `entry` metadata.
    - A destination reached *through* an element it shares with the previous one, as search is
      reached through Home's search bar, cross-fades (`SharedElementMotion`) and lets the element
      carry the motion. The door slide would drag the whole screen sideways while the bar grows
      upwards.
    - A destination that interrupts rather than follows, as the review of a scan does when the
      scanner closes, rises from the bottom edge over what is there and sinks back to it
      (`RisingMotion`). What is under it is held still: a step forward slides in from the side,
      and this is not one.
  - Shared elements animate in one `SharedTransitionLayout` around the display. Its scope reaches
    destinations through `LocalNavSharedTransitionScope`, not through `NavDisplay`'s own parameter:
    given the scope, the display wraps *every* entry in a shared element, and each destination
    starts gliding between panes. `Modifier.sharedBoundsAcrossDestinations(key, shape)` marks the
    two ends.

## Scenes: how the stack is laid out

Two scene strategies, tried in order. The first to claim the top entry wins.

1. **`OverlaySceneStrategy`** shows entries marked `OverlaySceneStrategy.overlay(...)` above the
   rest.
   - The container depends on the window: a modal bottom sheet below medium width, a dialog from
     medium width.
   - `OverlayPreference.AlwaysSheet` keeps lists of choices, such as document actions, in a sheet
     on any window.
   - The destination learns where it landed from `LocalOverlayContext`, including whether there is
     height to stack a header above its content.
2. **The list-detail strategy** (Material 3 adaptive) puts Home and the viewer side by side, when
   two panes really fit (expanded width). It is wrapped by `sharingTheWindow()`, so the
   destinations it lays out learn they share the window through `LocalPaneContext`. Everything it
   declines falls through to a single pane.
   - Search is a list pane too. On a wide window it takes Home's place beside the open document,
     so a result opens next to the results. Opening one replaces an open viewer but keeps search
     on the stack, so back returns to the results.
   - Picking a document from the list replaces the open one instead of stacking on it. Otherwise,
     every document looked at beside the list becomes a back step, and narrowing the window turns
     them into a trail the user has to back out of.

**Told, not measured.** A destination never reads the window size or the device orientation. In a
pane, or inside a dialog, those answers stop matching the room it actually has, and a screen that
measured for itself behaved as if it had the whole window. Layout that depends on a component's own
constraints is fine: that is measuring yourself, not the window. The viewer's toolbar, for one,
decides from its own width whether the zoom buttons fit.

## Modal destinations

**A sheet or dialog the user can reach, leave and come back to is a destination.** It goes on the
same back stack with its own key, and back means "take it off".
- Document actions, edit, delete, a document's details and *Go to page* all work this way.
- Rotating the device shows the same entry in another container. Form state lives in the entry's
  `rememberSaveable`, so it survives the change.

**Never put a `NavDisplay` inside a sheet or a dialog for such a destination.** This decision caused
more bugs than any other in this codebase. Home once rendered a hand-made stack in a
`NavDisplay` inside a `ModalBottomSheet`, and that had four consequences:
1. **The inner stack was not saved.** A process death lost it.
2. **Its pages were not entries**, so they got neither a ViewModel nor saved state of their own.
3. **Sheet-or-dialog and the inner stack were two states that had to agree**, and on rotation they
   did not.
4. **Back had two meanings.** Every press had to be routed by hand: inner stack, or outer? That
   code closed the wrong thing, or needed two presses.

A nested display is still right for a flow that is self-contained and discarded whole, such as an
onboarding inside a dialog. Floating windows (`ModalBottomSheet`, `Dialog`) have their own back
dispatcher, so back stays inside them.

**One destination, one entry, one back press.** If some UI wants to give back a second meaning on
the same surface, a destination is missing, or a state is one too many. The sheet container
follows this too: it only allows `Hidden` and `Expanded`, because a half-expanded state made the
first back press collapse the sheet instead of closing it.

**What is behind a modal destination goes out of focus.** While the top entry is shown as an
overlay, `DocucraftNavDisplay` blurs everything it draws (`outOfFocusBehindOverlay`, in
`NavigationMotion.kt`), on the motion scheme's effects spec, together with the scrim the container
draws. It asks the entry the same question the overlay strategy does
(`OverlaySceneStrategy.isShownAsOverlay`), so the two cannot disagree. The overlay is a window of its
own, so the blur never reaches it, and the sheet or dialog stays a solid surface. A dialog a screen
shows for itself, outside the back stack, blurs nothing: the appearance dialog is meant to show the
theme changing behind it.

**Transient popups anchored to content are not destinations.** A dropdown menu, or a link's
preview in the viewer, is attached to a spot on the screen, closed by any scroll, and meaningless
after a process death. It is UI state, not an entry.

## Requests from outside the UI

Something outside the composition may need a destination: the home-screen widget asking for a scan,
or *Go to page* returning a page to the viewer. It leaves a **standing request** in a small holder
that lives in Koin (`ScanRequestBus`, `ViewerPageRequests`). Whoever can act on it takes it when it
can, and consumes it.

A one-shot event is lost when nobody is listening at that moment. It then fires later, out of
context.

## The external viewer's stack

`PdfViewerActivity` opens other apps' PDFs in its own task, with its own back stack rooted at
`ExternalPdfViewer`. It is rendered by the same `DocucraftNavDisplay`, so details and *Go to page*
work as they do in the app. See [pdf-viewer.md](pdf-viewer.md#the-external-viewer-d5).
