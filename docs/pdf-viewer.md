<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# The PDF viewer

The feature that shows a document: bars, settings, details, *Go to page*, errors, and the viewer
for other apps' PDFs. Code: `app/.../feature/pdfviewer`. It draws with the engine
([pdf-engine.md](pdf-engine.md)); text selection and links are in
[text-and-links.md](text-and-links.md).

## Structure

```
navigation/     PdfViewerKey.kt: PdfViewer, ExternalPdfViewer, PdfDocumentDetails, GoToPage
domain/         ViewerDocumentRef, display-settings use cases, details, hand-off ports, links
data/           platform content provider, Custom Tabs opener, printer, session memory
presentation/   PdfViewerViewModel, PdfViewerScreen, sections, components
di/             PdfViewerModule, PageContentModule
```

- **One ViewModel per open document** (`PdfViewerViewModel`), keyed by a `ViewerDocumentRef`:
  - `Catalogued(uuid)` is a document the app owns, observed from the catalogue, so it closes if it
    is deleted;
  - `External(uri, displayName)` is another app's file.
- **What stays in the composition.** Page, zoom and pan are `PdfViewerState`, and whether the bars
  show is `ViewerChromeState`. None of it goes through the ViewModel: it is UI state, and a round
  trip per frame would buy nothing.
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
- **Actions in an `AppBarRow`**: Share, Print, Open with, Details. The row shows as many as fit in
  the bar's own width and moves the rest to its overflow menu, so a phone shows Share and "⋮" and a
  wide pane shows all four.
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
- **Fit mode**: a menu with the four modes by name, the current one checked.
- **Night mode**: a toggle button.

### Bars, scroller, indicator

- **One visibility for both bars.** Reading forward hides them, scrolling back shows them, and a
  tap toggles them.
  - They watch the viewer's nested scroll without consuming it. A top app bar's own scroll
    behaviour would consume the scroll, and stop the document while the bar moves.
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
- **Details** shows name, pages, size, description, and whether the document has text (see
  [text-and-links.md](text-and-links.md#text-in-details)). A catalogued document's details come
  from the catalogue; an external one's are read from the file.

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

**Known gap.** The intent filter accepts `http`/`https` PDFs, but the viewer reads through the
`ContentResolver`, so a remote PDF ends on the error screen. The engine can download
(`PdfSource.Remote`); the app does not use it yet.

## Analytics

- **A screen view per opened document**, logged by the ViewModel, whichever way the document was
  opened.
- **Settings changes**, as `PDF_VIEWER_SETTING_CHANGED`, with the setting's name and value.
