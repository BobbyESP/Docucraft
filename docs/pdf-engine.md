<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# The PDF engine (`:composepdf`)

A Compose PDF viewer on top of Android's `PdfRenderer`: it renders pages, lays them out, and
handles every gesture. It is generic on purpose. It knows pages, pixels and fingers, but never text
or links: the app adds those through the hooks below.

Public API: `composepdf/src/main/kotlin/com/composepdf/`. Internals: `…/internal/`.

## Using it

```kotlin
val state = rememberPdfViewerState()
PdfViewer(
    source = PdfSource.Uri(uri),
    state = state,
    layout = PdfLayoutSpec(fitMode = FitMode.WIDTH, contentPadding = PaddingValues(top = 64.dp)),
    style = PdfViewerDefaults.style(nightMode = true),
    onTap = { … },
    interactionHandler = handler,   // claim gestures (E3)
    overlay = { … },                // draw and place in page coordinates (E4)
)
```

- **Sources**: `File`, `Asset`, `Bytes`, `Stream`, `Uri` (through the `ContentResolver`), and
  `Remote`, which is downloaded into a disk cache first. The app only uses `Uri`.
- **Specs**:
  - `PdfLayoutSpec`: scroll direction; fit mode (`WIDTH`, `HEIGHT`, `BOTH`, `PROPORTIONAL`); page
    spacing and snapping; content padding.
  - `PdfZoomSpec`: limits, double-tap zoom, over-zoom.
  - `PdfGestureSpec`: which gestures are on.
  - `PdfRenderSpec`: quality and prefetch.
  - `PdfViewerStyle`: colours, page decoration, night mode, and the passive scroll indicator.
- **`PdfViewerState`** is the hoisted state. It holds the observable values `currentPage`,
  `pageCount`, `zoom`, `panX`/`panY`, `isLoaded` and `error`, and offers these commands:

  | Command | Does |
  |---|---|
  | `scrollToPage`, `animateScrollToPage` | centre a page |
  | `animateScrollTo(page, position)` | bring a point on a page to the start of the content area (E5) |
  | `setZoom`, `animateZoomTo`, `zoomIn`, `zoomOut`, `animateResetZoom` | zoom |
  | `panBy(delta)` | move the document at once, within bounds; used while a caller owns the gesture |
  | `hitTest(position)` | the page and page point under a viewer point |
  | `pageRectInViewer(page)`, `visiblePages` | geometry for overlays |

  Every animation and every touch goes through one `MutatorMutex`: starting one interrupts the
  other cleanly.
- **Load errors** arrive as `state.error: PdfLoadException`, with a `reason`: `NOT_FOUND`,
  `ACCESS_DENIED`, `PASSWORD_PROTECTED`, `DAMAGED` or `UNKNOWN`. The platform throws the same
  `SecurityException` for a revoked permission and for an encrypted file. The engine tells them
  apart where it still knows which step failed: opening the file, or parsing it.

## Inside

```
PdfViewer (Compose) ─ gestures (PdfGestures) ─┐
      │                                       ▼
      │                 PdfViewerController ── ViewerViewportCoordinator ── PageLayoutSnapshot (pure geometry)
      │                        │
      ▼                        ▼
PdfDocumentCanvas ◀── RenderEngine: PlanComputer ─ WorkQueue ─ workers ─ TileStore / PageBitmapStore
 (draws bitmaps)               │                                   ▲
                               ▼                                   │
                      PdfDocumentManager (2 PdfRenderers) ── BitmapPool
```

- **Geometry is pure.** `PageLayoutSnapshot` places pages in a "corridor" along the scroll axis,
  fits them to the content area, clamps pan and converts between screen, document and page
  coordinates. It is tested on the JVM. `ViewerViewportCoordinator` rebuilds it when the viewport,
  the pages or the config change.
- **Rendering is planned, not requested.** Every viewport change asks for a plan, through a
  conflated channel, so the planner always works from the latest viewport.
  - `PlanComputer` is pure and O(visible tiles). It decides which base pages and 512 px tiles
    should exist, in priority bands: visible base pages, visible tiles, look-ahead tiles, prefetched
    pages.
  - Each plan **replaces** the work queue: what is no longer wanted simply stops existing.
  - Workers re-check each tile against the live plan before rasterizing, and an epoch counter
    discards results from before an invalidation.
- **Two stores.**
  - `PageBitmapStore` keeps one base bitmap per visible page, the fallback that keeps a page from
    ever showing blank.
  - `TileStore` keeps sharp tiles on top.
- **Zoom levels are discrete.** Tiles are rendered at steps of √2, rounded up, so the caches stay
  reusable while pinching, and content is always downscaled on screen, never upscaled.
- **Memory.** Bitmaps are recycled through a 32 MB `BitmapPool` (`Bitmap.reconfigure`). A bitmap
  that may still be on screen is parked briefly before reuse.
- **Two renderers, two workers.** `PdfDocumentManager` opens the document twice (duplicated file
  descriptor), because `PdfRenderer` allows one open page at a time. Tiles of the same page are
  rendered under a single page open.
- **Night mode and scaling are applied when drawing**, never baked into bitmaps.

### Android 7

Android 7 (API 24 and 25) has two faults in its `PdfRenderer`. Each one kills the process in native
code, where nothing can catch it, and Android 8 fixed both. `PdfRenderers` (public, because the app
opens renderers of its own for previews and page counts) is how the app lives with them, and it
does nothing from Android 8 on.

- **Every renderer is opened through `PdfRenderers`**, never with the constructor.
  - The platform counts the renderers using its PDF library, to start it for the first and shut it
    down after the last. A renderer that fails to open (a password, a damaged file, not a PDF) is
    counted out twice, the second time by its finalizer. The count ends one short, and the library
    is shut down under a document that is open, or never started for the next one.
  - The count cannot be corrected, so it is offset. A one-page document of the engine's own is
    opened before the real one. If the real one opens, it is closed again. If not, it is never
    closed: it stands for the count the finalizer is about to take away.
- **Every call into a renderer or a page takes one lock.**
  - The PDF library is not made for two threads, and Android 7 does not keep them apart: two
    renderers drawing text at once corrupt the font cache they share. The engine's own two
    renderers are enough, and so are two previews.
  - `PdfRenderers.use` opens, hands over and closes a renderer under that lock, for the app's
    one-off uses. The engine keeps its renderers, so `PdfDocumentManager` and `PageRenderer` take
    the lock call by call, which is what the platform itself does from Android 8 on.

### Gestures

`PdfGestures` is one state machine, so gestures hand over to each other without dead frames: pan,
fling, pinch, double tap, quick scale (double tap and drag), tap and long press.
- **Nested scroll and overscroll.** Pan deltas go through nested scrolling (the app's bars watch
  it) and drive the platform's overscroll stretch.
- **Rubber-band zoom.** Zoom beyond the limits is resisted logarithmically, and springs back on
  release.
- **The viewer yields to its overlay (E2).** Overlay children see events first. If one consumes a
  down, or any event while the viewer is still undecided, the gesture is the child's, and the pages
  do not move. That is what makes selection handles and link previews draggable and tappable.

## Extension points

These are the only ways the app extends the engine. Keep new engine API as generic as they are.

- **E3 · `PdfInteractionHandler`: claiming gestures.**
  - `onLongPress(event): Boolean`. Return `true` to claim the gesture. The finger's movement then
    goes to `onDrag`, and nothing pans, pinches or flings. `onDragEnd` always runs, also on
    cancellation.
  - `claimsTap(event)` and `onTap(event)`. A claimed tap is delivered at once, without waiting to
    see whether a double tap follows. Everywhere else, double tap still zooms.
- **E4 · `PdfOverlayScope`: the overlay's receiver.** It is a `BoxScope` that can:
  - draw on every visible page (`DrawOnPages`, clipped to each page);
  - place an element at a point on a page (`Modifier.anchorTo(page, position, alignment,
    stayInside)`);
  - size an element to an area of a page (`Modifier.coverArea(page, area)`).

  Positions are page-normalized (`[0,1]²`, as the display shows the page). They follow pan and zoom
  in the draw, placement and measure phases, **never recomposing**.
- **E5 · `animateScrollTo(page, position)`.** It brings a page point to the start of the content
  area, where readers put a link's target. The other axis moves only if the point would be off
  screen. The target is clamped first, so the animation stops where the document ends.

## Reading position

Pan values mean nothing once the viewport or the page sizes change. The engine therefore keeps
positions as a `PageAnchor`: a page, plus a fraction along it.
- **The anchor is the centre of the content area**, which is also where the current page is read.
  Anchoring at the top edge made the current page change across a rotation.
- **Restoring after recreation (E1).** The saver stores the anchor and the zoom. The position is
  applied only once the document is laid out; applying it earlier used to lose it to the first
  load.
- **Layout changes without recreation.** A resize, a rotation the activity handles itself, a new
  fit mode or new padding: the controller takes the anchor before the change and returns to it
  after. It only does so when page sizes actually change, so a zoomed page panned sideways is not
  re-centred when only the height changes.
- **Content padding (E6).** `PdfLayoutSpec.contentPadding` is space kept clear for the host's bars.
  - Pages fit and settle inside it, and still draw underneath while scrolling, like `LazyColumn`'s
    padding.
  - The bars never resize the viewport. They used to, and the document was laid out again on every
    frame of their animation.

**Known limit.** Across the scroll axis the position is not restored: a zoomed document comes back
centred horizontally.

## Tests

- **JVM:** layout geometry (`PageLayoutSnapshotTest`), tile planning, zoom steps, the viewport
  coordinator, and load-error classification.
- **Instrumented, on a device:**
  - fixtures (`PdfFixturesTest`);
  - what Android 7's renderer does not survive (`PdfRenderersTest`), which only proves anything on
    API 24 or 25;
  - restoration and layout changes;
  - content padding;
  - gestures and their consumption;
  - the three extension points;
  - scroll-to;
  - load errors;
  - what the platform's content APIs report (`PlatformContentTest`).

See [testing.md](testing.md).
