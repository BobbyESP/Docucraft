<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Text and links

How the viewer reads what is on a page, lets the reader select and copy it, and follows links
safely. Code:
- `:document-content-api`: the contract and the pure selection logic;
- `app/.../feature/pdfviewer`: the providers, the ViewModel and the UI.

## The contract (`:document-content-api`)

Plain Kotlin, so that text recognition (OCR) can implement it from its own module, as ML Kit
implements the scanning contract.

```kotlin
interface PageContentProvider { val origin: ContentOrigin; suspend fun open(document: DocumentSource): PageContentSession }
interface PageContentSession : AutoCloseable { suspend fun page(index: Int): PageContentResult }

sealed interface PageContentResult { Available(text: PageText?, links) · NoText · Unsupported · Failed(cause) }
PageText(lines: List<TextLine>, origin: EMBEDDED | RECOGNIZED, confidence?)
TextLine(words) · TextWord(text, bounds, glyphs?)          // glyphs: one box per character
PageLink.External(bounds, uri) · PageLink.Internal(bounds, pageIndex, position?)
```

- **Page-normalized coordinates.** Everything is `[0,1]²` over the page as displayed, with the crop
  box and rotation applied, origin at top left. It is the same space the engine's overlay uses.
- **"No text" and "cannot read" are results, not failures.** The reader is told something
  different for each.
- **A session holds the document open** while it is on screen. Opening is costly; a provider keeps
  its own state in the session (a renderer, a recognizer, a cache).
- **`open` never throws for an unreadable document.** Its pages come back `Failed`.

## Providers

They are bound in `PageContentModule.kt`. The two readers have a name each, `EMBEDDED_TEXT` and
`TEXT_RECOGNITION`, and the viewer reads with the unnamed one:

```kotlin
LayeredPageContentProvider(
    embedded = PlatformPageContentProvider(context),
    recognized =
        CatalogueRecognizedTextProvider(documents, pages, MlKitTextRecognitionProvider(context)),
)
```

- **`PlatformPageContentProvider`** reads the PDF's own text layer through `PdfRenderer`'s content
  APIs, which need API 35+.
  - It opens its own renderer, not the engine's. Content can then be read without a screen, and
    never competes with drawing.
  - **Below API 35 every page is `Unsupported`**, and the file is not even opened (**D1**). No
    second PDF library, and no higher `minSdk`: the degradation is the same path a scanned page
    takes, and text recognition covers both.
- **`LayeredPageContentProvider`** tries the embedded text first. Only for a page with none
  (`NoText`, `Unsupported`, or links only) does it ask `recognized`, opened lazily, once per
  session.
  - Recognized text keeps the document's own links.
- **`MlKitTextRecognitionProvider`** (`:ocr-mlkit`) reads a page from its image: the page is drawn
  at about 200 dpi on white, recognized on the device with ML Kit, and each word's box is given as
  a fraction of the page. ML Kit is an `implementation` dependency of that module, so none of its
  types reach `:app`; the engine is swapped at the `TEXT_RECOGNITION` binding.
  - The model is Google Play services'. A page read before it has arrived fails, and is read again
    later.
  - The document is opened for each page with `PdfRenderers.use`, never held: a renderer that is
    kept has to take turns with every other one on Android 7.
- **`CatalogueRecognizedTextProvider`** is what the viewer gets as `recognized`. Text recognition
  is the user's choice for each document, so the viewer does not recognize whatever page is on
  screen: it shows what was already recognized and stored, and only recognizes a page itself when
  its document has recognition on and the background reading has not reached that page yet. A
  document that is not in the library has no recognized text.

### What the platform really gives (verified on API 37)

- **Text.** `getTextContents()` returns one block per page, lines separated by `\r\n`, and **no
  geometry**.
- **Geometry.** It comes from `selectContent(start, end)` by character index. The provider measures
  **every character** that way; a word's box is the union of its characters. If a character cannot
  be measured, the word is measured whole and its characters are spread evenly.
- **Cost.** About 0.1 ms a character. On the densest fixture (about 2,700 characters a page) that is
  180 ms a page on average, 358 ms at worst. It runs off the main thread, and results are cached in
  an LRU of 12 pages per session.
- **Scanned pages.** An image-only page has blank text, which is how `NoText` is detected.
  `getImageContents()` is useless for this.
- **External links.** `getLinkContents()` reports them correctly. A link over two lines comes as
  **one** rectangle enclosing both (`QuadPoints` are ignored), which can cover nearby text.
- **Internal links.** `getGotoLinks()` returns **nothing**, for every form tried: explicit
  destinations, `/GoTo` actions, ReportLab tables of contents. That held on the emulator and on a
  Pixel. The code handles them, and `PlatformContentTest` has a canary that fails the day the
  platform starts reporting them.

`PlatformContentTest`, in `:composepdf`, pins all of the above against the fixtures.

## Selection

Pure logic in `:document-content-api`, tested on the JVM. It behaves like the reference viewers
(Google Drive's among them):
- **a long press selects the word** under the finger;
- **from then on both ends move character by character**, whether the same finger drags on or a
  handle is dragged later;
- **the pressed word stays selected** whichever way the drag goes.

### The model

- **`TextSelection` handles one page.** It lays the page out as its text will be pasted (`text`):
  words separated by a space, lines by a line break, and a blank line kept between paragraphs. A
  selection on it is a `TextSpan`: two carets, half-open.
- **What is copied is exactly the characters between the handles**, in the document's reading
  order. A backwards drag, a selection over several lines, and a right-to-left line therefore all
  come out as read. A table stored column by column is copied column by column, as the reference
  viewers do.
- **`DocumentSelection` runs across pages.** It stores only its two ends (`TextCaret(page, offset)`).
  Each page works out its share when drawn or copied, so a selection over many pages costs nothing
  until it is used.

### Geometry: runs, not lines

A provider's "line" is not always a strip of the page. A note set along the margin comes as one
line as tall as the page, and a line can run on from one column into the next. The geometry works
on **runs** instead: words of one line that sit together and read the same way.
- **A run's direction** is left to right, right to left, top to bottom, or bottom to top. It comes
  from its glyphs, or from its neighbours for a one-letter word.
- **A finger picks the run nearest across that run's direction.** For a normal line that is its
  height; for vertical text, its width. Near-ties go to the run nearest along its direction, as
  for the columns of a table row. Within the run, it picks the nearest boundary between characters.
- **Highlights are one box per run, as tall as the line**, not as the letters. Handles sit at a
  character's leading or trailing edge.
- **Without glyphs**, a word's box is shared evenly among its characters. That is exact for
  monospaced text, and good enough for OCR that only gives word boxes.

### In the viewer

- **Reading ahead.**
  - The screen reports the visible pages after 150 ms without movement, so a fling only reads
    where it stops.
  - The ViewModel reads those pages ±1, plus the pages the selection ends on, and forgets the
    rest.
- **Deciding a touch.**
  - A long press is decided synchronously from what has been read (`SelectionInteraction`). The
    engine's claimable long press (E3) then hands the drag over, so the document stays still.
  - A page not yet read does not claim the press.
- **Drawing.** The highlight, the two handles and the system text toolbar (Copy, Select all) are
  drawn in the engine's overlay (E4), so they follow pan and zoom without recomposing.
  - The handles hang outward, so on a short selection their touch targets do not cover each other.
  - A handle's drag point is taken from the first touch, and aims slightly into the line.
- **Auto-scroll.** Dragging near the top or bottom of the content area scrolls the document, up to
  1200 dp/s, and the selection follows even while the finger holds still.
- **Letting go.** A tap or Back clears the selection. Copying also clears it.
- **Select all** takes every page the selection touches, not the whole document: copying a long
  document means measuring all of it.
- **Night mode** highlights with `inversePrimary`, because the normal selection colour hardly
  shows on inverted pages.
- **TalkBack** announces the selection through a live region.

### When there is nothing to select

A long press on a page without text gives a rejection haptic and, once per document for each
reason, a notice:
- "This page is an image…" for a scan;
- "Selecting text in PDFs needs Android 15…" below API 35.

The press is not claimed, so the finger can still pan.

### Text in details

`DetectDocumentTextUseCase` looks at the first 5 pages. The details dialog shows the answer:
Selectable, Recognized, None (images only), Unsupported, or unknown. It shows "Checking…" until the
answer arrives.

## Links

### Deciding (`ResolveLinkUseCase`)

Every link goes through one use case before anything opens. It is a security rule, so it lives in
the domain, with tests. A PDF link's visible text need not match where it goes.
- **Only four schemes are followed**: `http`, `https`, `mailto` and `tel`. Everything else is
  refused: `javascript:`, `file:`, `intent:`, `content:`, `data:`…
- **A web link is shown by the host a browser would really open**, parsed as browsers do (WHATWG):
  - no userinfo: `https://bank.com@evil.com` shows `evil.com`;
  - a backslash ends the host;
  - tabs and line breaks are dropped;
  - no port, no case;
  - internationalized names as punycode, so a look-alike (`аpple.com`, with a Cyrillic "а") shows
    as `xn--pple-43d.com`.
- **Plain `http`** is marked not secure.
- **An internal link** to a page the document does not have is refused.

### Showing (D3)

A tap on a link is claimed by the viewer (E3), so it answers at once, without the double-tap wait.
- **It never opens anything by itself.** It shows a preview under the link:
  - for the web: the host, the full URL, and "Connection not secure" for `http`, with **Copy link**
    and **Open**;
  - for email or phone: the address or the number, with **Write** or **Call**;
  - for a refused link: why, and no Open.
- **The preview is not a destination.** It is attached to the link and closed by any scroll, a tap
  elsewhere, or Back.
- **An internal link is followed at once**: it is reversible. "Back to page N" returns to the exact
  line the reader was on.
- **While text is selected**, a tap is not claimed: it lets go of the selection.

### Opening (D4)

`LinkOpener`, from the activity, never from the application context. With a new task, a Custom Tab
would open outside the app, and back would not return to the document.
- **Web**: a Custom Tab from a browser that offers them (`androidx.browser`), coloured from the
  theme, in the app's own task, so back returns to the document. If no browser offers Custom
  Tabs: `ACTION_VIEW`. With nothing at all: "No app can open this link".
- **Email**: `ACTION_SENDTO`. **Phone**: `ACTION_DIAL`, which types the number and calls nothing.
- **`<queries>`.** The manifest declares these intents. Since API 30, without them no browser would
  ever seem to offer Custom Tabs.

### Accessibility

Links are drawn into the page, so TalkBack cannot see them. Each link on the visible pages gets an
invisible node over its area, through the overlay's `coverArea`. It is a button described by where
it leads ("Link: example.com", "Phone link: …", "Blocked link"), and it opens the preview.

## Known limits

- A link over two lines is one rectangle that may cover nearby text.
- Internal links are never reported by the platform.
- A line that mixes directions (bidi) is highlighted from its first selected character to its last.
- Selection follows the document's order: in a form stored column by column, dragging down a
  column also takes in whatever the document stores in between.
