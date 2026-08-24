# Phase 2 — Home Screen Design Proposal

**Status:** Design direction, approved for implementation in Phase 3
**Scope:** Home screen only. No navigation-architecture changes (Phase 4), no performance work, no architectural refactor.
**Baseline:** `HomeContent.kt`, `ScannedDocumentListItem.kt`, `ScreenPlaceholderCard.kt` at commit `84f27b6`.
**Design system:** Material 3 Expressive, `androidx.compose.material3:1.5.0-alpha22` (full Expressive surface verified available).

---

## 0. Audit validation

Phase 1's findings were re-checked against the code. Not all of them survived.

### Confirmed — structural, must fix

| # | Finding | Evidence |
|---|---|---|
| 1 | The search field is rendered inside `Scaffold(floatingActionButton = ...)`, sharing a `Row` with the FAB | `HomeContent.kt` — `floatingActionButton` slot contains `Row { SearchBar(weight 1f); ExtendedFloatingActionButton(...) }` |
| 2 | Consequence of #1: an 88dp phantom `Spacer` is appended to the list to compensate for the occluded area | `ScannedDocumentsList` — `item(contentType = "bottomBarActionsSpacer")` |
| 3 | Consequence of #1: a whole-screen `detectTapGestures` exists solely to drop search focus | `Scaffold(modifier = modifier.pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } })` |
| 4 | Consequence of #1: rotation re-focuses the field, requiring `LaunchedEffect(orientation) { clearFocus() }` | Commented in-file as a platform default-focus workaround |
| 5 | `isEmptyResult` is computed in `HomeUiState` and never consumed — searching to zero results shows a bare sort header | `HomeUiState.isEmptyResult`; no reference in `HomeContent` |
| 6 | Error state titles every failure "Unknown error" and offers no retry | `ErrorContent(...)` uses `R.string.unknown_error` as title; `HomeIntent.Load` exists but is unreachable from the UI |
| 7 | Sticky sort header uses a transparent gradient — content scrolls through it and becomes illegible | `Brush.verticalGradient(listOf(background, Color.Transparent), startY = 100f)` |
| 8 | `ScreenPlaceholderCard` runs an unbounded `infiniteRepeatable` rotation in a state that can persist for the whole session | `rememberInfiniteTransition` → `rotationZ` |
| 9 | The list renders `description ?: "No description"` and never surfaces `createdTimestamp`, `pageCount`, or `fileSize` — the user can sort by date/name/size on data that is not visible | `ScannedDocumentListItem`; `ScannedDocument` model |
| 10 | Thumbnails are clipped to `MaterialShapes.Slanted` over a `primaryContainer` fill — a parallelogram crop and a primary tint applied to actual page content | `ScannedDocumentListItem` — `imageModifier` |

### Confirmed — worth fixing, lower severity

11. List item title uses `bodyMedium`; a list item's primary label belongs on the title scale.
12. The trailing overflow `IconButton` is filled with `secondaryContainer`, giving every row a high-emphasis coloured control that competes with the thumbnail.
13. `TopAppBar` has no `scrollBehavior`; ~64dp is permanently spent rendering the app name the user just tapped to get here.
14. The search placeholder is dimmed with `Modifier.alpha(0.66f)`, which drops it below the 4.5:1 contrast floor.
15. Grouped list items sit on `surfaceColorAtElevation(1.dp)` against `background` — a difference too small to read as a group boundary.
16. Selected state is conveyed by container colour alone, with no non-colour cue and no `selected` semantics.
17. `hasActiveFilters` is a second dead state field, alongside `isEmptyResult` — neither is referenced outside `HomeContract.kt`. `FilterOptions` supports page-count, file-size and date-range filters that have no UI at all. Not in scope to build a filter sheet this phase, but the state should not claim a capability the screen does not offer.

### Rejected as subjective — not changing

- The 2dp inter-item gap. It is a deliberate, consistent choice and it works.
- Settings as a top-app-bar icon. Correct placement for a single low-frequency destination.
- Font family selection and the `createTypography` weight overrides. Out of scope and not defective.

### Explicitly preserved — these are good

- **The MVI contract.** `HomeUiState` / `HomeIntent` / `HomeEffect` is clean and near-complete. This redesign needs only two additive intents.
- **The Navigation3 list-detail scene** with `ListDetailSceneStrategy.listPane(detailPlaceholder = ...)`. Home is already correctly modelled as the list pane.
- **The connected list-group shapes** in `DocucraftShapeDefaults` (`largeIncreased` outer, `extraSmall` inner). This is the current M3 list-group idiom and it is well executed.
- **`animateItem` with motion-scheme springs.** The best motion decision in the screen — see §6.
- **Haptics on sort change** (`HapticFeedbackType.SegmentTick`).
- **`CircularWavyProgressIndicator`** — a correct Expressive loading choice.
- **The bottom-anchored primary action instinct.** Right instinct, wrong execution.

---

## 1. Purpose of the home screen

DocuCraft is a scanner. A user opening it has one of two intents:

**A — Capture.** "There is a receipt/contract in front of me right now." Time-critical, physical context, often one-handed.
**B — Retrieval.** "I need to find the document I scanned and send it." Deliberate, less urgent.

B is more frequent over the app's lifetime. A is the reason the app exists and is the only one that is time-critical. This resolves the hierarchy:

- **Primary action:** Scan. Must be reachable in under a second, one-handed, and must never scroll away.
- **Primary content:** the document library, newest first.
- **Understood in the first three seconds:** *"These are my scanned documents, newest first, and I can scan another right now."*
- **Persistent:** the scan action, and the document list.
- **Contextual:** search (a mode, not a control), sort (low frequency), per-document actions (already correctly in a sheet).
- **Secondary:** settings.

The screen's job is a **library with one dominant verb**. It is not a dashboard, and it should not acquire stat tiles, recent-activity cards, or suggestion rows.

---

## 2. Proposed structure

```text
HomeScreen  (Scaffold, edge-to-edge)
 │
 ├── topBar : MediumFlexibleTopAppBar (exitUntilCollapsed)
 │      ├── title     "Documents"
 │      ├── subtitle  "12 documents · 4.2 MB"      ← null when the library is empty
 │      └── actions   [ Search ] [ Settings ]
 │
 ├── content : LazyColumn
 │      ├── pinned header — Sort row (opaque surface, divider appears on scroll)
 │      │      ├── ButtonGroup: [ Date ] [ Name ] [ Size ]   (connected, single-select)
 │      │      └── IconToggleButton: sort direction
 │      │
 │      └── Document group  (connected list items, 2dp gaps)
 │             └── ScannedDocumentListItem
 │                    ├── thumbnail   A4, shapes.small, surfaceContainerHighest
 │                    ├── title       titleMedium
 │                    ├── metadata    "24 Aug · 5 pages · 1.2 MB"   bodySmall / onSurfaceVariant
 │                    └── overflow    IconButton (plain)
 │
 ├── floatingActionButton : ExtendedFloatingActionButton "Scan"
 │      └── expanded at rest and at list top; collapses to icon while scrolling
 │
 └── overlays
        ├── Search  → full-screen expanded search view (own state, own empty result)
        └── DocumentDialogWrapper (unchanged)
```

**States replacing the single content slot:**

```text
content
 ├── Loading        → delayed 150ms, then LoadingIndicator (centred)
 ├── Empty library  → full-bleed empty state, no card, FAB hidden
 ├── Empty results  → in-list "no matches" block, search query preserved
 ├── Error          → specific title + cause + Retry (primary button)
 └── Idle           → sort row + document group
```

---

## 3. Component decisions

### 3.1 Search — the central change

| | |
|---|---|
| **Current** | `TextField` inside `Surface`, placed in `Scaffold`'s `floatingActionButton` slot, sharing a `Row` with the FAB, gated on `hasDocuments` |
| **Proposed** | Icon in the top app bar that opens a full-screen M3 expanded search view |
| **Why change** | The FAB slot is a single-action slot, not a layout container. Putting a persistent text input there breaks the `Scaffold` insets contract, forces the 88dp spacer, forces the global tap-to-dismiss handler, forces the rotation focus workaround, and places search last in the TalkBack traversal order. Every one of those hacks disappears when search moves. |
| **Why better** | Search is a **mode**, not a control. It is entered deliberately, occasionally, and it wants the whole screen when it is active — for the query, the results, and a real no-results state. Making it permanently occupy the bottom 72dp of every session is a bad trade against a feature used in a minority of sessions. |

**On thumb reach.** Moving search to the top is a genuine ergonomic cost, and it was the right instinct to put it low. The trade is worth taking: search is low-frequency and deliberate, one tap brings the field and keyboard *to* the user, and — decisively — freeing the bottom is what lets the primary action own it uncontested. This matches Files, Drive, and Photos.

### 3.2 Primary action

| | |
|---|---|
| **Current** | `ExtendedFloatingActionButton` at ~50% width of a row it shares with search; collapses when search takes focus |
| **Proposed** | `ExtendedFloatingActionButton` alone, bottom-end; `expanded` at rest and at list top, collapsed while scrolling |
| **Why change** | A primary action that shares its row at equal visual weight with a secondary control is not primary. |
| **Why better** | Uncontested placement, thumb-reachable, labelled for first-run comprehension, and the collapse gives content back during scroll. `expanded` is now driven by *scroll*, which is meaningful, instead of by *search focus*, which was a workaround. |

**Rejected: `HorizontalFloatingToolbar`.** This is Expressive's flagship bottom pattern and it would be the obvious thing to reach for. It does not earn its place here. A toolbar is justified when there are two-to-five peer actions to group; this screen has exactly one creation path. `HomeIntent` has no import action, so a toolbar would ship with one real button and filler. **If import-from-file lands** (`CopyDocumentToFileUseCase` and the `filekit` dependency suggest it may), the correct answer is a `SplitButton` — Scan as the primary half, a menu with "Import PDF" as the secondary half — or `HorizontalFloatingToolbar(floatingActionButton = ...)` at three or more actions. That is the documented extension point, not a reason to add the component now.

### 3.3 Sort controls

| | |
|---|---|
| **Current** | Sticky `LazyColumn` header, `SelectionGroupRow` (LazyRow of `ToggleButton`) + `VerticalDivider` + filled `IconButton`, over a transparent gradient |
| **Proposed** | Pinned row on an **opaque** `surface`, with a `ButtonGroup` of connected toggle buttons and an `IconToggleButton` for direction; a hairline `outlineVariant` divider fades in only once the list has scrolled |
| **Why change** | The gradient is the defect, not the control. Content scrolling through a semi-transparent header produces overlapping text. |
| **Why better** | Opaque background guarantees legibility at any scroll offset; the divider communicates "there is content above" only when true. `ButtonGroup` is the M3 component built for single-select criteria and brings connected shape morphing on press for free — the existing `SelectionGroupRow` is a hand-rolled approximation of it. |

Keeping the row **pinned rather than scrolling away** is deliberate: sort must remain reachable deep in a long list without scrolling to the top.

### 3.4 Document list item — the highest-value change

| | |
|---|---|
| **Current** | Slanted-clipped thumbnail on `primaryContainer`; `bodyMedium` title; `description ?: "No description"`; `secondaryContainer`-filled overflow button |
| **Proposed** | A4 thumbnail with `shapes.small` on `surfaceContainerHighest`; `titleMedium` title; a **metadata line** `"24 Aug · 5 pages · 1.2 MB"`; plain overflow button |
| **Why change** | Three separate problems. (a) The description slot is usually empty, so most rows spend their entire second line on the literal words "No description". (b) The data that *is* always present and always useful — date, page count, size — is never shown, which means the three sort criteria all operate on invisible properties. (c) `MaterialShapes.Slanted` crops real page content and the `primaryContainer` fill recolours a preview image. |
| **Why better** | The metadata line makes the sort controls legible in the content itself: sorting by size becomes meaningful when sizes are on screen. A rounded rectangle at the true A4 ratio shows the page. The title moves to the scale its role calls for. Description moves to the action sheet and detail pane, where there is room and where it was authored. |

**Optional polish, not core:** emphasise whichever metadata field matches the active sort criterion. Pleasant, but it couples the item to sort state — defer until the core lands.

### 3.5 Empty state

| | |
|---|---|
| **Current** | `ScreenPlaceholderCard` — `ElevatedCard` + 1dp border + a `Cookie6Sided` blob rotating on an infinite 6s loop, centred |
| **Proposed** | Full-bleed centred column: static `MaterialShapes` icon container, `headlineSmall` title, `bodyLarge` body, filled "Scan a document" button. No card, no border, no perpetual motion |
| **Why change** | The empty state *is* the screen; drawing a card boundary around a screen-sized element communicates nothing, and `ElevatedCard` + explicit border is double emphasis. The infinite rotation costs battery and frame budget for the entire time the state is visible, and it is an unavoidable moving target for motion-sensitive users. |
| **Why better** | This is the one screen where expressive typography genuinely earns its place — there is no competing content, so the type *is* the content. Keeping the `MaterialShapes` container while dropping the rotation retains the Expressive personality and removes the cost. One entrance animation replaces the infinite one. |

The FAB stays hidden in this state (already correct today) — two "Scan" affordances on an empty screen is one too many.

### 3.6 Error state

| | |
|---|---|
| **Current** | Title hardcoded to "Unknown error"; the real message demoted to body; no action |
| **Proposed** | Title = "Couldn't load your documents"; body = the actual cause; **Retry** as a filled primary button dispatching `HomeIntent.Load` |
| **Why change** | Calling every failure "Unknown error" while holding a specific message one line below is the exact anti-pattern to avoid, and a dead-end error state in an app whose data is entirely local is unnecessary — retry is nearly always the right move. |
| **Why better** | The user learns what failed and gets the one action that can fix it. Colour is used with restraint: the icon carries `errorContainer`, the recovery button is `primary`. The screen does not turn red. |

### 3.7 No-results state — new

`isEmptyResult` already exists in `HomeUiState` and is unused. It renders in-place: an icon, *"No documents match "invoice""*, and a **Clear search** text button dispatching the existing `HomeIntent.ClearSearch`. The query stays visible and editable — never dump the user back to an unfiltered list without telling them why the screen changed.

### 3.8 Loading state

| | |
|---|---|
| **Current** | `CircularWavyProgressIndicator` centred, shown for any non-zero duration |
| **Proposed** | Suppress for the first ~150ms, then `LoadingIndicator` |
| **Why change** | The source is a local Room query that typically resolves in well under 100ms, so the dominant visual result today is a spinner *flash* on every cold start — motion that carries no information. |
| **Why better** | The indicator appears only when there is a real wait. The Expressive morphing `LoadingIndicator` then reads as intentional rather than as a glitch. |

---

## 4. Material 3 Expressive decisions

Each entry answers: *why does this improve the experience here?*

| Expressive element | Where | Why it earns its place |
|---|---|---|
| `MediumFlexibleTopAppBar` | Screen header | Carries a **title + subtitle** in one component, letting the library count and total size live in the header instead of consuming a list row. Collapses on scroll, so the space is spent only when the user is at the top and reading orientation cues. A plain `TopAppBar` cannot hold the subtitle; a `LargeFlexible` bar is too tall for a utility list. |
| `ButtonGroup` (connected toggles) | Sort criteria | The purpose-built component for single-select criteria. Connected shape morphing on press gives tactile confirmation for a control whose effect (a re-sort) happens *elsewhere* on screen. Replaces a hand-rolled `LazyRow` of `ToggleButton`s. |
| `LoadingIndicator` | Delayed loading | Expressive shape-morphing indicator, shown only when a wait is real. |
| `MaterialShapes` container | Empty state only | The one place with no content behind it, so a distinctive shape adds personality at zero legibility cost. Deliberately **not** applied to thumbnails, which contain real content. |
| Motion schemes (`defaultSpatialSpec`, `slowSpatialSpec`) | List re-sort, FAB | Already in use and correct. Spring physics make the re-sort read as items *moving* rather than teleporting. |
| Emphasized type weight | Selected list item | A non-colour cue for selection, at no layout cost. |
| Connected list-group shapes | Document group | Already implemented and correct. Outer 20dp vs inner 4dp encodes "these items are one collection" through shape rather than a container. |

**Expressive components considered and rejected:**

- **`HorizontalFloatingToolbar`** — only one action to hold. See §3.2.
- **`SplitButton`** — no second creation path exists yet. Named as the correct answer *if* import ships.
- **`Carousel`** — a "recent scans" carousel would duplicate the top of the list beneath it, and page thumbnails are visually near-identical, so a hero treatment surfaces less information than a text row. Rejected.
- **`ShortNavigationBar` / navigation components** — out of scope; Phase 4.
- **Display type scale** — excessive. This is a utility library screen, not a hero surface. Headline is the ceiling.
- **Shape morphing on the list items** — decorative here; the group shapes already do the semantic work.

---

## 5. Visual language

### Typography

| Role | Style | Note |
|---|---|---|
| App bar title (expanded) | `MediumFlexibleTopAppBar` title token | "Documents" — names the content, not the app |
| App bar subtitle | app bar subtitle token | "12 documents · 4.2 MB"; `null` when empty |
| List item title | `titleMedium` | Up from `bodyMedium` |
| List item metadata | `bodySmall`, `onSurfaceVariant` | |
| Sort toggles | `labelLarge` | Component default |
| Empty-state title | `headlineSmall` | Up from `titleLarge`; the type is the content here |
| Empty-state body | `bodyLarge` | Up from `bodyMedium` |
| Error title | `headlineSmall` | Matches empty state — one state design language |

Display scale is used nowhere.

### Shape

| Element | Shape | Rationale |
|---|---|---|
| List group outer corners | `largeIncreased` (20dp) | Preserved |
| List group inner corners | `extraSmall` (4dp) | Preserved — shape communicates grouping |
| Thumbnail | `small` (8dp) | Subordinate to the item radius; shows the page |
| Sort toggles | `full` | Component default |
| FAB | `large` | Component default |
| Empty-state icon container | `MaterialShapes` | The only decorative shape on the screen |

Shape carries hierarchy: strong outer radii for collections, weak inner radii for members, subordinate radii for content inside a member.

### Colour

**Primary is reserved for exactly two things: the Scan FAB, and the recovery/CTA button in the empty and error states.** Nothing else on the screen uses it.

| Surface | Colour |
|---|---|
| Screen background | `surface` |
| List group items | `surfaceContainerLow` — up from `surfaceColorAtElevation(1.dp)`, which was too close to the background to read as a group |
| Selected item | `secondaryContainer` / `onSecondaryContainer` (preserved) |
| Sort toggle, selected | `secondaryContainer` (component default — secondary, not primary) |
| Metadata text | `onSurfaceVariant` |
| Pinned-row divider | `outlineVariant` |
| Error icon | `errorContainer` / `onErrorContainer` — the icon only |

**Removed primary usage:** thumbnail background (`primaryContainer` → `surfaceContainerHighest`), overflow button fill (`secondaryContainer` → none), sort-direction button fill (`surfaceContainerHighest` → none).

### Spacing

One rule, applied everywhere: **16dp horizontal margin on compact, 24dp on medium and above.** The app bar, the pinned sort row, the list group, and every state screen share the same margin, so nothing on the screen is optically out of alignment with anything else. Vertical rhythm on the 8dp scale. The 2dp intra-group gap is the single deliberate exception and it is what makes the group read as connected.

---

## 6. Motion

Every animation on this screen must respond to user input or communicate a state change. Nothing decorative.

| Motion | Trigger | What it communicates |
|---|---|---|
| App bar collapse | Scroll | Reclaims vertical space; the header shrinks to a title as the user commits to browsing |
| FAB extend / collapse | Scroll direction | Gives content back during scroll and re-offers the labelled action at rest |
| **List item re-placement** | Sort change | **The most valuable motion on the screen.** The list visibly re-sorts, which is the entire point of the sort control. Already implemented with `slowSpatialSpec`. Preserved unchanged. |
| Content cross-fade | Empty ⇄ list ⇄ error | Only between materially different states; skipped for loading→idle inside the 150ms threshold |
| Press state layers / shape morph | Touch | Component defaults; no custom press animation |

**Removed:** the infinite blob rotation (§3.5), and the custom `customOverscroll` + `Modifier.offset { }` translation on the `LazyColumn`, which fights the platform's own overscroll effect and drives an offset read on every frame. Platform overscroll is the correct behaviour and is free.

**Not added:** shimmer skeletons, parallax, staggered entrance animations, hero blur.

---

## 7. Responsive behaviour

The rule: **more space buys more information per row, not more columns.**

| Window | Layout |
|---|---|
| **Compact** (<600dp, portrait) | Single column, 16dp margins, `MediumFlexibleTopAppBar`, FAB bottom-end |
| **Compact height** (phone landscape) | Fall back to the standard `TopAppBar` — a two-row flexible bar consumes an unacceptable share of a short window. Driven by window **height** size class, not width |
| **Medium** (600–839dp, full-width) | List constrained to `widthIn(max = 640.dp)` and centred; 24dp margins. Prevents a full-bleed row where the title sits inches away from its metadata |
| **Expanded** (840dp+) | Home is the **list pane** of the existing `ListDetailSceneStrategy` scene and stays narrow beside the open document. Already correct — unchanged this phase |
| **Expanded, no detail open** | The existing `NoDocumentOpenPane` placeholder holds the detail pane. Unchanged |
| **Large / extra-large full-width** | Same single column at max width; larger thumbnail and a fourth metadata field. Density, not columns |

**A two-column grid is explicitly rejected.** Documents are identified by *name*, and scanned page thumbnails are visually near-identical to one another. A grid trades the title and metadata line — the information that actually distinguishes rows — for larger pictures of white rectangles. A tablet gets a deliberately wider, denser single column; it does not get a magnified phone, and it does not get a Pinterest board.

---

## 8. Accessibility

Designed in, not retrofitted.

- **Touch targets.** Overflow `IconButton` at 48dp. Sort toggles wrapped in `minimumInteractiveComponentSize()` — `ButtonDefaults.MinHeight` resolves to `ButtonSmallTokens.ContainerHeight` = **40dp**, and drops to **36dp** when `shouldUsePrecisionPointerComponentSizing` is on. Both are below the 48dp floor, so the wrapper is required, not optional.
- **Font scaling.** No fixed item heights anywhere. At large scale the item title relaxes from 1 line to 2 rather than truncating. The metadata line truncates from the tail — date is the most useful field and survives longest.
- **TalkBack.** The list item merges into one node reading *"Invoice August, 24 August, 5 pages, 1.2 megabytes"* — the `·` separators are visual only and must never be announced. The overflow button stays a separate focusable node with its own label. Removing the search field from the FAB slot fixes the traversal order, which currently announces the search input after all list content.
- **Selection without colour.** Selected items get `secondaryContainer` **plus** emphasized title weight **plus** `selected` semantics, so the state survives greyscale, colour-blindness, and TalkBack.
- **Contrast.** The `Modifier.alpha(0.66f)` on the search placeholder is removed — the M3 placeholder colour role already meets contrast, and multiplying alpha on top of it breaks the 4.5:1 floor. Metadata at `onSurfaceVariant` on `surfaceContainerLow` passes in both schemes.
- **Motion sensitivity.** Removing the infinite rotation eliminates the only unavoidable, non-dismissible motion on the screen. All remaining motion is input-driven and brief.
- **Semantics of decorative elements.** Empty- and error-state icons take `contentDescription = null`; the text carries the meaning.
- **Removed interference.** The screen-wide `detectTapGestures` handler goes away with the bottom search field — a full-screen gesture handler competing with list taps is a hazard for switch access and exploratory touch.

---

## 9. Contract impact

The MVI contract barely moves, which is a sign the redesign is a presentation change rather than a rearchitecture.

**Additive only:**
- `HomeIntent.Retry` — or simply reuse the existing `HomeIntent.Load` from the error state. No new type strictly required.
- `HomeIntent.SetSearchActive(Boolean)` — search becomes a mode, so the mode needs a state bit. (Note: a `ToggleSearchBarVisibility` intent was removed in `84f27b6`; this reintroduces the concept deliberately, as a mode flag rather than a visibility flag.)
- `HomeUiState.isSearchActive: Boolean`.

**Now consumed, previously dead:** `HomeUiState.isEmptyResult`.

**Unchanged:** every use case, every repository, the sheet contract, the navigation entry, and the list-detail scene strategy.

---

## 10. Implementation order for Phase 3

Sequenced so each step is independently shippable and verifiable.

1. **List item** — metadata line, `titleMedium`, thumbnail shape and colour, plain overflow button. Highest value, zero structural risk, no contract change.
2. **States** — error with retry and a specific title; no-results wired to `isEmptyResult`; empty state de-carded and de-rotated; loading delayed.
3. **Sort row** — `ButtonGroup`, opaque pinned background, scroll-reactive divider. Delete the gradient.
4. **Top app bar** — `MediumFlexibleTopAppBar` with subtitle and `exitUntilCollapsed`; height-aware fallback in landscape.
5. **Search extraction** — move out of the FAB slot into the app bar and a full-screen search view. Delete the 88dp spacer, the global tap handler, and the rotation focus workaround.
6. **FAB** — scroll-driven `expanded`, sole occupant of the bottom-end slot.
7. **Responsive** — max content width and margin tokens.

Steps 1–4 touch no structure and can land first. Step 5 is the one with real surface area, and it *removes* more code than it adds.
