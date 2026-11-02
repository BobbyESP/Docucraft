<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# The PDF viewer

The feature that shows a document: bars, settings, details, *Go to page*, errors, and the viewer
for other apps' PDFs. Code: `:feature:pdfviewer`. It draws with the engine
([pdf-engine.md](pdf-engine.md)); text selection and links are in
[text-and-links.md](text-and-links.md).

## Structure

```
navigation/     ViewerKeys.kt: ExternalPdfViewer, PdfDocumentDetails, SaveCopyToLibrary, GoToPage
                (PdfViewer, the key other features open, is in :core:navigation)
domain/         ViewerDocumentRef, display-settings use cases, details, hand-off ports, links
data/           platform content provider, Custom Tabs opener, printer, session memory
presentation/   PdfViewerViewModel, screens/, sections, components
  screens/      PdfViewerScreen (the ViewModel, effects and back), PdfViewerContent (what is
                drawn), PdfViewerScreenState (what the screen holds), PdfViewerEffects
di/             PdfViewerModule (PageContentModule, which chooses the page readers, is the app's)
```

- **One ViewModel per open document** (`PdfViewerViewModel`), keyed by a `ViewerDocumentRef`:
  - `Catalogued(uuid)` is a document the app owns, observed from the catalogue, so it closes if it
    is deleted;
  - `External(uri, displayName)` is another app's file.
- **What stays in the composition.** Page, zoom and pan are `PdfViewerState`, and whether the bars
  show is `ViewerChromeState`. None of it goes through the ViewModel: it is UI state, and a round
  trip per frame would buy nothing. `PdfViewerScreenState` holds them together with the search bar
  and the bars' heights, and what each needs of the others: closing the search shows the bars,
  a match is revealed between them.
- **The screen never hands the ViewModel down.** `PdfViewerScreen` collects its state, its effects
  and its messages, and `PdfViewerContent` draws from that state and sends intents back.
- **What the ViewModel holds:**
  - the document;
  - the display settings;
  - the text and links of the pages near the screen;
  - the selection;
  - a link's preview.

  It also sends effects for what needs the activity: print, open a link, copy, jump to a page.

## Display settings (D2)

- **Settings → Document viewer** can turn on app-wide defaults: a fit mode and night mode. With
  them on, every document opens with them. With them off, documents open with the factory
  settings: fit to width, night mode off (A2).
- **Changes made while reading apply to that document only.** They last for the session: the
  process's lifetime, held in `InMemoryViewerSessionSettings` and shared by both activities.
  Reopening the app starts from the defaults again.
- **A process death is still the same session (A1).** The session memory is lost with the process,
  so each ViewModel also copies a chosen setting into its `SavedStateHandle`. It does so even for a
  choice inherited from the session memory, because that is still what the user chose.
- **Resolution.** `ObserveViewerDisplaySettingsUseCase` resolves the setting as: the session's
  value if there is one, else the defaults if they are on, else the factory settings. It is tested
  case by case in `ViewerDisplaySettingsTest`.

## Reading position

A document opens where it was left, on the same line, whatever the screen it is opened on.

- **What is kept** is a page and a fraction along it (`ReadingPosition`), in the document's row of
  `document_activity`. It is the engine's own reading position
  ([pdf-engine.md](pdf-engine.md#reading-position)), which does not depend on the viewport.
- **When it is written.** A second after the reader stops moving, and when they leave: when the
  screen is disposed, and when the activity stops, which is the last moment an app is sure to get.
  Never on every frame of a scroll.
  - What is written on leaving is the last position *seen*, not one read at that moment: by then
    the engine may have let go of the document.
  - The write runs in the app's scope, not the ViewModel's. It starts as the viewer leaves, and
    would be cancelled with it.
- **Where the document opens is known before it is shown.** The ViewModel reads the position
  first (`start` in its state), and the screen is only composed once it has it, so the pages are
  laid out once, in place. Showing them earlier would open the document at its start and then
  jump.
  - It is only where the state *starts*. After a rotation or a process death the engine restores
    where the reader was, which is later than anything written down.
- **A position is always read as a place the document has** (`ReadingPosition.within`). A file can
  be replaced by a shorter one between two readings; the reader is then taken to its last page.
- **A document that moves is read where it was.** Saving another app's document into the library
  changes where it is read from while it is open. The viewer and its state are keyed by that
  location, and the new ones start at the last position seen: the engine does not take a new state
  under a viewer that is already laid out, and starts a document it has not seen from the top.
- **It can be turned off**: Settings → Document viewer → *Remember where I left off*, on by
  default. Off means the app does not remember: `SetReadingPositionMemoryUseCase` also forgets
  every position already kept, and nothing is written while it is off.

## The screen

```
Phone                                     Tablet / wide pane
┌──────────────────────────────────┐      ┌─────────────────────────────────────────────┐
│ ←  Invoice March           ⇪   ⋮ │      │ ←  Invoice March        ⇪  🖨  ↗  ⓘ         │
│    Paid on 12/03                 │      │    Paid on 12/03                            │
├──────────────────────────────────┤      ├─────────────────────────────────────────────┤
│            (pages)              ▐│      │                 (pages)                    ▐│
│  ╭──────────────────────────╮    │      │   ╭────────────────────────────────────╮    │
│  │  3 / 12   ▭ Fit    ☾     │    │      │   │ 3 / 12 │ −  100%  + │ ▭ Fit │ ☾    │    │
│  ╰──────────────────────────╯    │      │   ╰────────────────────────────────────╯    │
└──────────────────────────────────┘      └─────────────────────────────────────────────┘
```

### Top bar (`PdfViewerTopBar`)

- A standard Material 3 `TopAppBar`: the document's name as the title, and its description as the
  subtitle.
- **Actions**: Search (always a button, see [Search](#search)), then Share, Print, Open with, Details. As many as fit in the bar's own width are buttons
  and the rest go in a menu, so a phone shows one and "⋮" and a wide pane shows all four.
  - **The menu is the app's own**, not the one Material's `AppBarRow` brings: two groups, what is
    done with the document and what it is, each frosted over the pages (`FrostedMenuGroup`). It has
    a shadow and no blur halo, unlike Home's sort menu: a halo blurs what was recorded around the
    menu, which is the pages, and this menu opens from the bar, which is not in that recording, so
    the halo drew the pages over the bar's buttons.
  - **Save to Docucraft** comes before them, for a document that belongs to another app, and only
    once it is on screen. It is then the one action a narrow bar shows: without it the document is
    on loan. It goes when the document is saved, from here or from anywhere else, because the
    ViewModel follows the document in the catalogue
    ([scanning.md](scanning.md#documents-of-other-apps)).
  - Share and Open with are left out for a document that cannot leave the app (`canBeHandedOff`:
    only `content://`).
  - Print is left out until there is a document.
- **Back** is shown only when the pane does not already offer a way back.
- Every icon button has a tooltip, and a label for TalkBack.
- **Frosted** over the pages, which scroll beneath it (see
  [architecture.md](architecture.md#blur)).

### Bottom toolbar (`PdfViewerBottomToolbar`)

A vibrant `HorizontalFloatingToolbar`, frosted in its vibrant color over the pages instead of
shadowed:
- **Page chip** ("3 / 12"): opens *Go to page*.
- **Zoom.** The − / % / + group appears only when the toolbar has at least 600 dp of its own width,
  such as on a tablet. Narrower, a zoom chip shows only while the zoom is not the fitted one, and
  tapping it returns to fit. Zoom always animates.
- **Fit mode**: a menu with the four modes by name, the current one selected. It is frosted with a
  shadow, as the top bar's menu, for the same reason: the toolbar is not in the pages' recording.
- **Night mode**: a toggle button.

### Search

Finding a word in the open document. The logic is in
[text-and-links.md](text-and-links.md#search-in-the-document); this is the screen.

```
Closed                     Open, typing                       After the keyboard's search key
┌────────────────────┐     ┌────────────────────────────┐     ┌────────────────────────────┐
│ ←  Title   🔍 ⇪ ⋮  │     │ ( ←  invoice            ✕ ) │     │ ( ←  invoice            ✕ ) │
├────────────────────┤     ├────────────────────────────┤     ├────────────────────────────┤
│       (pages)      │     │   …the ▓invoice▓ is due…   │     │   …the ▓invoice▓ is due…   │
│                    │     │     ╭──────────────╮       │     │                            │
│                    │     │     │ ⌃  2 / 14  ⌄ │       │     │     ╭──────────────╮       │
└────────────────────┘     ├─────╰──────────────╯───────┤     │     │ ⌃  2 / 14  ⌄ │       │
                           │        keyboard            │     │     ╰──────────────╯       │
                           └────────────────────────────┘     └────────────────────────────┘
```

- **Opening.** A button of its own in the top bar, never in the menu: it is what a reader reaches
  for while reading. Hidden until a document is on screen.
- **Arriving from a search.** Opening a document from the library's search carries the query in
  its `PdfViewer` key. The viewer lights the matches and scrolls to the first, without the find
  bar, and puts them out when the reader first touches the document.
- **`FindBar` takes the top bar's place**, with its height and frost, so the pages (padded by that
  height) do not move. The field is a pill with a back button and a clear button.
- **`FindToolbar` is at the bottom**: the floating toolbar in the bottom toolbar's color, with the
  arrows and the counter. It sits above the keyboard (`WindowInsets.ime`) so the thumb reaches it
  while typing, and appears once there is a query.
  - The counter reads "3 / 12" and slides in the direction the reader moves. A ring beside it shows
    reading progress. TalkBack hears it when the search settles, not for every page.
  - With nothing found it says so, or says why when the document is the cause (images only, or a
    device that cannot read the text); the arrows are then off.
  - It rises through the insets, not by measuring. Below API 30 the keyboard's insets only reach an
    activity that resizes (`adjustResize`) and the app's theme pans (`adjustPan`), so the toolbar
    may stay under the keyboard there. Not checked on a device.
- **Both bars stay while searching** and the page pill keeps out: stepping scrolls the document,
  which would otherwise hide them. Closing the search brings them back.
- **The keyboard.** Its search key dismisses it and keeps the matches; so does touching the
  document, which the keyboard covers. A hardware Enter goes to the next match, Shift+Enter to the
  previous.
- **Back** dismisses the keyboard (the system does), then closes a link preview, then clears a
  selection, then leaves the search.
- **`reveal`** moves the document only if the match is outside the free area between the bars and
  the keyboard, so stepping through matches on one screen keeps the reader's place. A match out of
  view lands about a third of the way down, with some text before it.
- **What survives.** Whether the bar is open and the typed text are presentation
  (`ViewerSearchState`, saved like the bars' visibility); what was found is the ViewModel's.
  - After a rotation the ViewModel still has the search and its active match; the screen tells it
    the same query, which changes nothing, and says nothing while the document reloads.
  - After a process death the ViewModel is new and the same query searches again from the reader's
    page.

### Bars, scroller, indicator

- **One visibility for both bars.** Reading forward hides them, scrolling back shows them, and a
  tap toggles them.
  - They watch the viewer's nested scroll without consuming it. A top app bar's own scroll
    behaviour would consume the scroll, and stop the document while the bar moves.
  - They follow where the scroll is heading, not its last pixel: the document has to travel
    `ScrollHeadingThreshold` one way first (`ScrollHeadingTracker`, shared with the buttons that
    float over the app's lists). A finger that settles moves the pages back a little without the
    reader having turned around.
  - They are the viewer's constant content padding, so the document never moves when they do.
- **Fast scroller.** On documents of 3 or more pages it sits on the trailing edge, inside the
  padding. It shows while the document moves and fades after 1.2 s. Dragging its thumb jumps
  through the document with the page number beside it, and TalkBack sees it as an adjustable
  control. It replaces the engine's passive indicator.
- **Page pill.** With the bars hidden, a small "N / total" shows at the top when the page changes:
  hiding the bars must not hide where the reader is.

### Go to page, details

- **Both are destinations** (`GoToPage`, `PdfDocumentDetails`): a sheet on a phone and a dialog on
  a wide window, chosen by the overlay strategy ([navigation.md](navigation.md#modal-destinations)).
- ***Go to page*** returns the page through `ViewerPageRequests`, a standing request per document
  that the viewer consumes once it has scrolled there.
- **Details** says what is known of the document, in three parts.
  - **The document**, as the card that introduces a scan in its review (`DocumentHeroCard`,
    [scanning.md](scanning.md#reviewing-a-scan)): its first page, its name and description, its
    pages and size, and whether it is a favorite. The page's badge says where it came from.
  - **In your library**, for a document the app keeps: its folder, its tags, when it was scanned or
    imported, and when its content last changed, if it has.
  - **File**: what its file is called, when that is not its name; the version of PDF it is; and
    whether it has text (see [text-and-links.md](text-and-links.md#text-in-details)). The text is
    one row among the others: it is a fact of the file, not what the details are about.
  - A catalogued document's details come from the catalogue; an external one's are read from the
    file. What the catalogue does not keep, the version and the text, is read from the file for
    both, once for as long as the document stays where it is: not again when it is renamed or
    tagged while the details are open.
  - **The version is read from the file's first bytes**, not with a renderer. The PDF's own
    metadata (author, subject, creation date) and the size of its pages are not shown: nothing
    reads them yet, and `PdfRenderer` is only for counting pages outside the content provider.
  - Its body is written once, on `OverlayForm`, which is what every other overlay of the app is
    built on. A fact that is not known is left out rather than shown empty.
  - A fact that is not known is left out, not shown as a dash: another app's document may have no
    size or page count to give. Whether it has text is always said.

### Loading and errors

- **Loading**: the expressive `LoadingIndicator`.
- **Errors.** An icon, a title and a message chosen from the engine's `PdfLoadException.reason`
  (`ViewerLoadError`): not found, access denied, password-protected, damaged, or unknown. The
  exception's name is never shown.
- **Actions on the error screen:**
  - *Open with another app*, only when another app could reach the file: not when it is gone, or
    unreadable;
  - *Back*, when the viewer shows one.
- **On error**, the bottom toolbar hides, and so do the Share and Open with actions for a file the
  app cannot reach.
- **The catalogue is told how it went** (`DocumentLoaded`, `DocumentFailedToLoad`): the file was
  there, it was gone, or it could not be read for lack of permission. Recents shows that the next
  time, instead of failing again. A protected or damaged document was reached, and counts as
  there.

## The external viewer (D5)

`PdfViewerActivity` answers `VIEW` and `SEND` for `application/pdf`, so that other apps can open
their PDFs in Docucraft.
- **Its own task** (`taskAffinity=""`, `autoRemoveFromRecents`). Closing it returns to the app that
  opened it, and never closes, or shows, Docucraft's own library.
- **Its own back stack**, rooted at `ExternalPdfViewer`, rendered by the same `DocucraftNavDisplay`.
  Details and *Go to page* therefore work exactly as in the app. Back at the root finishes the
  activity.
- **It handles rotation itself** (`configChanges`). The engine keeps the reading position across
  the resize (see [pdf-engine.md](pdf-engine.md#reading-position)).
- **A `file://` PDF is shown, but never handed on** to another app.
- **The document is registered in the catalogue**, as one that belongs to another app, by the
  viewer's ViewModel as it opens ([scanning.md](scanning.md#documents-of-other-apps)). That is what
  gives it a uuid to note its opening and its reading position against. The viewer goes on showing
  what it was handed, and if registering fails the document is shown all the same.
- **A document the library already has is said to be there**, once, with *Open*
  (`PdfViewerEffect.AlreadyInLibrary`). It is known a moment after the document shows, when its
  file has been hashed. The document handed over stays open: the notice only offers the library's
  one, which has the reader's place in it and does not depend on the other app. *Open* puts that
  document on top, as a `PdfViewer` destination of this stack, so back returns to the one handed
  over.

**Known gap.** The intent filter accepts `http`/`https` PDFs, but the viewer reads through the
`ContentResolver`, so a remote PDF ends on the error screen. The engine can download
(`PdfSource.Remote`); the app does not use it yet.

## Analytics

- **A screen view per opened document**, logged by the ViewModel, whichever way the document was
  opened.
- **Settings changes**, as `PDF_VIEWER_SETTING_CHANGED`, with the setting's name and value.
