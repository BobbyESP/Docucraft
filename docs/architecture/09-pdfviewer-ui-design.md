<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Phase 3 · PDF viewer — UI design (step b8)

> Replaces §7 of the [target architecture](07-pdfviewer-target-architecture.md), which the
> maintainer set aside on 2026-09-24: the new interface is a full rewrite that follows Material 3
> Expressive more closely, not a restyle of the current bars. Progress is tracked in the
> [migration plan](08-pdfviewer-migration-plan.md), step b8.
>
> Decisions taken with the maintainer on 2026-09-24: a standard top app bar; the document's
> description as its subtitle; no FAB for now; the zoom buttons only where there is room for them.

---

## 1. What was wrong with the current bars

**Top bar** (`PdfViewerTopBar`), design:
- Not a Material component: a hand-made "pill" (`Surface` in `primaryContainer`, tonal elevation,
  fixed margins) whose saturated colour competes with the document.
- Forced typography: `titleLargeEmphasized` plus an extra `SemiBold`, and a subtitle dimmed with
  `alpha(0.66f)` instead of the `onSurfaceVariant` role.
- *Share* was the one emphasised action (a filled icon button), which it is not in a reader.
- In the list-detail layout the pill floated over the detail pane without adapting to it.

**Top bar**, behaviour:
- The back arrow was announced as "Cancel" (B3).
- Print, open with and details lived in the overflow menu whatever the room available.
- Icon buttons had no tooltips.
- It hid on scroll through an ad-hoc flag but only came back on a tap; the bottom bar never hid.
- The subtitle was the description *or* the page count, which the bottom bar shows as well.

**Bottom toolbar** (`PdfViewerBottomToolbar`), kept in spirit but rewritten:
- Its own three-tier metrics (`BoxWithConstraints` breakpoints, 32 dp icon buttons) instead of the
  component's tokens.
- A text field inside the toolbar to jump to a page, which raised the keyboard under the bar (S2).
- Zoom − / + buttons on every width, instant rather than animated, and a hidden horizontal drag on
  the percentage.
- A fit-mode button that cycled through four modes without saying which one was on.

## 2. The design

```
Phone                                     Tablet / wide pane
┌──────────────────────────────────┐      ┌─────────────────────────────────────────────┐
│ ←  Invoice March           ⇪   ⋮ │      │ ←  Invoice March        ⇪  🖨  ↗  ⓘ         │
│    Paid on 12/03                 │      │    Paid on 12/03                            │
├──────────────────────────────────┤      ├─────────────────────────────────────────────┤
│                                 ▐│      │                                            ▐│
│            (pages)              ▐│      │                 (pages)                    ▐│
│  ╭──────────────────────────╮    │      │   ╭────────────────────────────────────╮    │
│  │  3 / 12   ▭ Fit    ☾     │    │      │   │ 3 / 12 │ −  100%  + │ ▭ Fit │ ☾    │    │
│  ╰──────────────────────────╯    │      │   ╰────────────────────────────────────╯    │
└──────────────────────────────────┘      └─────────────────────────────────────────────┘
```

### Top app bar

- **A standard `TopAppBar`** with the expressive subtitle slot, attached to the top edge in surface
  colours. *Why*: it is the Material pattern for a content screen with a title and actions.
- **Title**: the document's name. **Subtitle**: its description, or nothing. *Why*: the current page
  already has a home in the bottom toolbar; saying it twice wastes the line.
- **Actions in an `AppBarRow`**: *Share*, *Print*, *Open with*, *Details*, in that order. The row
  shows as many as fit and moves the rest into its overflow menu. A phone shows *Share* and "⋮", and
  a wide pane shows all four. *Why*: the component already knows how much room it has, so the bar
  adapts without the screen measuring the window, which `AGENTS.md` forbids. *Share* and *Open with*
  are left out for documents that cannot leave the app (step b3).
- **Standard icon buttons**, none of them filled, each with a tooltip.
- **Back**: announced as "Back" (fixes B3), and shown only when the pane does not already provide a
  way back (`LocalPaneContext`).

### Bottom toolbar

- **`HorizontalFloatingToolbar`** with the vibrant colours the maintainer liked, and default
  component metrics. No hand-tuned sizes.
- **Page chip** "3 / 12": opens *Go to page* (§2.4).
- **Zoom**: the − / % / + group appears **only when the toolbar has room for it**, as the
  maintainer asked. On narrow widths, only a zoom chip remains, and only while the zoom differs from
  fit; tapping it goes back to fit. All zoom changes animate. *Why constraints and not window
  size*: the viewer can be a pane of a list-detail layout, so the room the toolbar actually gets is
  what matters, and measuring its own constraints is layout, not window-sniffing.
- **Fit mode**: an icon button that opens a menu with the four modes by name and the current one
  checked.
- **Night mode**: an icon toggle button with the expressive shape change when checked.
- **No FAB**, for now. The toolbar's FAB slot is kept free for search or OCR later.

### Showing and hiding the bars

- One visibility for both bars. Reading forward hides them, scrolling back shows them, and a tap
  toggles them. *Why*: it is the standard Material behaviour for bars over content. The previous
  scheme had three tap states and bars that behaved differently.
- The bars observe the viewer's nested scroll without consuming it. *Why not the components'
  own scroll behaviours*: the top app bar's collapses by consuming the scroll in `onPreScroll`. With
  bars drawn over the pages, that would stop the document for as long as the bar is moving.
- The bars stay the viewer's constant `contentPadding` (step b7): the document never moves when they
  do.

### Go to page

- A **destination** (`GoToPage` key), as `AGENTS.md` requires for a dialog the user can open and
  leave: a sheet on phones, a dialog on wide windows, both chosen by the overlay strategy. It holds a
  number field bounded to the document's pages.
- It returns the page through a small retained request holder keyed by document. The viewer takes
  the request, scrolls to the page with an animation, and consumes it. *Why retained*: it is the same
  lesson as `ScanRequestBus` in the navigation phase. A request made while the viewer is not
  listening must wait, not vanish.

### Fast scroller and page indicator

- A **fast scroller** on the trailing edge, inside the content padding, for documents of three or more
  pages. It appears while scrolling and fades out after a pause. Dragging its thumb jumps through the
  document and shows the page number beside it. It replaces the engine's passive scroll indicator.
- While the bars are hidden, a small "3 / 12" pill fades in at the top when the page changes. *Why*:
  hiding the bars must not hide where the reader is.

### Loading and errors

- **Loading**: the expressive `LoadingIndicator`.
- **Error** (fixes B4): an icon, a title and a message chosen by cause: password-protected, no
  permission to read, or damaged. Actions: *Open with another app*, when the document can leave the
  app, and *Back*, when the viewer shows a way back. The exception's class name is never shown.

### Motion and accessibility

- All motion from `MaterialTheme.motionScheme`; changing numbers (page, zoom) animate with
  `AnimatedContent`.
- Every icon button has a label, used both for its tooltip and for TalkBack. Toggles expose their
  state. Touch targets come from the component defaults (48 dp).
