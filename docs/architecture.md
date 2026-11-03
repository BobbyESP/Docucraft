<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Architecture

How the app is put together, and why. Where each piece lives is in [`AGENTS.md`](../AGENTS.md);
each subsystem has its own document, listed in the [index](README.md).

## Modules

```
:app  the shell ─────────────┬──> :feature:home, :search, :organization, :bin, :scan,
  │                          │      :document-actions, :pdfviewer, :settings, :widgets
  │                          │        (never another feature)
  │                          ├──> :library:ui ──> :library:domain   (plain Kotlin)
  │                          ├──> :library:data ──> :library:domain, :core:data
  │                          └──> :core:ui ──> :core:navigation ──> :core:designsystem ──> :core:domain
  │
  └──> engines, bound only here: :scanner:mlkit ──> :scanner:api
                                 :content:platform, :content:mlkit ──> :content:api
                                 :composepdf
```

The module graph, and the build that checks it, enforce what conventions would only ask for:
- **Domains are plain Kotlin.** `:core:domain` and `:library:domain` are JVM modules: an Android or
  Compose import does not compile, so every rule they hold is testable on the JVM.
- **Screens do not reach storage.** Only the app depends on `:library:data` and `:core:data`; a
  feature sees the catalogue through the ports of `:library:domain`, bound by the app's DI.
- **Features do not know each other.** A feature opens another by a key, and those keys live in
  `:core:navigation`, which every feature depends on. Two features that open each other (Home and
  folders, by way of the document actions) would otherwise be a cycle Gradle refuses. Keys are plain
  data, uuids at most, so one place for all of them costs nothing: a change inside a feature
  recompiles that feature and the app, nothing else.
- **The build refuses what breaks these rules.** Every feature applies `docucraft.android.feature`,
  which lists what a feature may depend on: `:core:*` and `:library:*` except their `:data`, the
  engines' contracts (`*:api`) and `:composepdf`. Anything else fails the build with the rule it
  breaks. It is a list of what is allowed, so a new module is refused until someone decides a
  feature may see it.
- **Engines are replaceable.** `:scanner:mlkit` and `:content:mlkit` depend on ML Kit as
  `implementation`, and `:content:platform` is the only module using the platform's content APIs.
  Only the app names them, in `DocumentScannerModule` and `PageContentModule`: swapping an engine
  means another module and one line of DI. An engine is a folder with its contract (`:api`) beside
  its implementations, so what a feature may see is told by the name alone.
- **The engine stays generic.** `:composepdf` knows nothing of the app: no text, no links, no Koin.
  The viewer extends it through public hooks ([pdf-engine.md](pdf-engine.md)).

A module's code lives under its namespace (`core.ui`, `library.data`, `feature.home`…), so a package
names its module. Four classes keep their old package (`feature.docscanner.*`) because their names
are stored outside the code: the widgets people placed know their receivers by name, and WorkManager
stores the class of queued work. A new name would remove the widgets or leave the work failing.

The build logic is the included build `build-logic`: one convention plugin per kind of module
(Android library, Compose, feature, Room, plain Kotlin), so a module's build file names only what is
its own.

## Layers inside a feature

```
presentation   screen ─ intents ─> ViewModel ─ state/effects ─> screen
                                       │
domain         use cases ─ ports (interfaces) ─ models          plain Kotlin
                                       │
data           port implementations: Room, DataStore, files, platform APIs, other apps
```

- **The domain is plain Kotlin**: no Android, Compose or data layer, so every rule is testable on
  the JVM. A location is a `ContentRef` (a URI as a string), never an `android.net.Uri`, which
  cannot be built in a plain JVM test. A model is not annotated for Compose: strong skipping
  (on by default) lets a composable that takes it skip when the instance is the same.
- **Anything the framework does goes behind a port**: an interface in the domain, implemented in
  data (`DocumentStorage`, `SearchIndex`, `DocumentSharer`, `LinkOpener`, `PageContentProvider`,
  `DocumentScanner`…). Tests use fakes, not mocks of the framework.
- **A port that needs an `Activity`** (printing, opening a link) is a Koin `factory` given the
  activity through `parametersOf`. The ViewModel emits an effect, and the screen, which has the
  activity, calls the port.

### Results, not exceptions

When the user can cause it, or it is simply how things are, the answer is a sealed result: a
cancelled scan or export, a page that is only an image, a device too old to read a PDF's text, a
link not safe to open. `ScanOutcome`, `ExportOutcome`, `PageContentResult` and `LinkAction` are all
sealed. A generic `Result<T>` would put "the user changed their mind" in the same bucket as "the
disk is full".

## ViewModels (MVI)

Every screen's ViewModel extends `core/ui/viewmodel/BaseViewModel<Intent, State, Effect>`:

| Channel | For | When nobody is listening |
|---|---|---|
| `state` (`StateFlow`) | what the screen shows | always has a value |
| `effects` (`SharedFlow`, no replay) | commands for whoever is on screen *now*: print, open a link, scroll | **dropped** |
| `defaultEvents` (buffered `Channel`) | messages for the user | **kept** until shown |

The asymmetry is deliberate: a lost message is a silent failure, while a command carried out later,
against whatever the user is doing by then, is a wrong action.

- ViewModels never hold a `Context`; text comes from `StringProvider`.
- UI-only state stays in the composition: scroll, zoom, whether bars show. The viewer's
  `PdfViewerState` is to a PDF what `LazyListState` is to a list.
- What must survive process death goes in the entry's `SavedStateHandle`. Navigation 3's entry
  decorators give each destination its own ViewModel and saved state.

## Dependency injection

Koin, started in `App.kt`. Each feature binds its ViewModels in a module of its own, the library
binds its ports in `:library:data`, and the app binds app-wide services and the engines. A new
module is added to the list in `App.kt`.

**Swapping an implementation is always one binding:** the scanner (`DocumentScannerModule.kt`),
where page text comes from (`PageContentModule.kt`), how the library is searched (the `SearchIndex`
binding in `ScannedDocumentModule.kt`). If a swap needs more, the design is not finished.

## App-wide services in the UI

Composition locals in `core/ui/common/CompositionLocals.kt`: `LocalDarkTheme`,
`LocalSettingsRepository`, `LocalNotificationsService` and `LocalAnalyticsHelper`. Screens read them
instead of injecting the same services again.

## Theme

`DocucraftTheme` (`core/designsystem/theme/Theme.kt`) is Material 3 Expressive, with colors from the
wallpaper (Android 12+) or generated by MaterialKolor from the user's seed color and palette style.
The app reads `MaterialTheme` as usual. Type roles Material has no slot for, such as the user's
monospace font, are fields of `DocucraftTheme.typography`.

**The default fonts are packaged in `res/font`, never downloaded** (`ThemeMapper.kt`,
`FontDefinitions.kt`). A downloadable font loads asynchronously, so the first frames use a fallback
and the text visibly swaps; that is unacceptable for the fonts every user sees on every start. A
packaged preset resolves to its local files whatever role it is chosen for. Others go to Google
Fonts and show a packaged font of the same kind meanwhile (Inter for sans-serif, JetBrains Mono for
monospace), so the swap is between similar shapes. A new preset must choose one path in the
exhaustive mapping.

**A color scheme is built only when its inputs change**, off the main thread: MaterialKolor takes
several milliseconds per scheme, too much for the frame that switches the theme. Only the first
scheme is built in place, since the first frame needs it. The last few are cached, so switching back
builds nothing.

**A theme change moves the scheme itself, in a fixed number of steps** (`ThemeTransition.kt`).
Material provides the color scheme through a static composition local, so each scheme given to it
recomposes everything under the theme. That is the cost of a change, and it is bounded: the way
from one scheme to the next is cut into at most 10 schemes over 100 ms, whatever the display's
refresh rate. Animating frame by frame (MaterialKolor's `animate = true`) would cost one per frame
of a longer animation, and more on faster displays.
- **The app stays live throughout.** Fading a picture of the last frame over the new theme would
  cost one recomposition, but the picture stands still over whatever moves beneath it.
- **An interrupting change continues from what is showing**, so tapping through seed colors never
  jumps back.
- **Colors are mixed in Oklab**, as Compose's `lerp` does, so a light theme goes dark through greys
  of its own hue.

**A screen with a color of its own nests a theme** (`DocucraftAccentTheme`, and `LabelColorTheme` for
a palette color), for a destination about something the user gave a color to. A folder's contents
(`FolderContentsScreen`) use the folder's color; the root, and a folder without one, use the app's.
- **Only that screen is recomposed, once per color.** Changing the app's theme on the way in and
  back on the way out would recompose every screen twice.
- **Its color is switched to, never stepped towards.** The screen gets its color while the
  navigation animates it in, and each step of a transition would recompose the whole screen during
  that animation, which stutters. The palette's colors are built ahead of time, in the background,
  a second after each change of the app's theme (`PrepareLabelColorThemes` in `MainActivity`), so
  a folder's color is ready when the folder is read and arrives with its contents, in the same
  recomposition. Any other color is built in the background under the app's scheme, then swapped
  in. Without a color it passes the app's scheme through, so it follows the app's own changes step
  by step; with one, it hides those steps, and the screen recomposes only once, for its new color.
- **The color is a hue, not a value**: it is pulled towards the theme's primary and built in the
  theme's style and darkness (`DocucraftTheme.paletteStyle`), so a screen in its own color looks
  like the rest of the app. A label draws the same primary roles, without building the scheme
  (`accentRoles`; [organization.md](organization.md#colors-and-icons)).
- Overlays opened over that screen (a folder's actions, its editor) are destinations of their own
  and stay in the app's theme.

**Components never animate colors for a theme change.** A component animates its own state (pressed,
selected, enabled, scrolled) and reads colors from the theme every frame, as `SettingsItemIcon` and
Home's top bar do with a fraction and `lerp`. Material's own components still animate colors when
their target changes; during a theme change the motion scheme gives them an all but immediate
spring so they do not trail the screen. It must be a spring, not a snap: each step moves the target
of an animation under way, and Compose keeps a component's own spec for an interrupted animation
only when it is a spring.

**A motion scheme returns the same spec every time.** Material remembers what a component is
animating by its spec (a button's shape morph is kept in `remember(animationSpec)`). A new spec per
call makes every recomposition look like a change of spec, so the button restarts from the shape it
was heading to and a press jumps to the pressed shape instead of morphing. The theme's scheme wraps
Material's to keep components up with a change, and otherwise returns Material's own specs
(`ThemeTransitionMotionScheme`).

### Blur

Material has no blur tokens; `DocucraftBlurDefaults` (`core/designsystem/theme/`) decides where the
app blurs and by how much. There are two kinds, for two jobs.

**A surface floating over content that moves beneath it is frosted, not shadowed.** What is behind
it shows through, blurred, under its translucent Material container color; a shadow's dark edge
would separate it from the content, the frost does the same and keeps the content in view. It uses
[Haze](https://github.com/chrisbanes/haze): the content is recorded with `Modifier.hazeSource`, and
the surface applies `Modifier.frosted` with `DocucraftBlurDefaults.surfaceStyle(containerRole)`,
built on Haze's Material 3 style. The surface's own container becomes transparent and loses its
shadow elevation. Used by Home's app bar (once the list scrolls under it), search bar and sort menu;
every other list screen's `FrostedLargeTopAppBar` (`core/designsystem/components/ScreenChrome.kt`);
the search field over the results; and the viewer's top bar and floating toolbar over the pages.

Rules that keep it working:
- **The source is a sibling of what frosts it, never an ancestor**, or the surface would blur
  itself. A popup is the exception that works: it is another window, so the sort menu frosts the
  list it opened from.
- **Content scrolls beneath the surface, padded rather than inset**, or there is nothing to frost:
  Home's list takes the scaffold's padding as content padding.
- **`HazeInput.Sources`** also reaches across windows; Haze's native backdrop cannot.

**An element floating over content is lifted by a blur halo, not a shadow** (`Modifier.blurHalo`,
`BlurHalo.kt`). The content around it goes out of focus, most at its edge, and is sharp again
`HaloSpread` away (a menu's: `MenuHaloSpread`); a faint veil of the surface color fades with the
blur, so the element keeps a soft rim over a flat area. The halo is a Haze blur on an area grown
around the element, without changing its layout. The radius really decreases with distance: a small
shader gives Haze the intensity at each point from the signed distance to the element's shape. A
blurred copy fading over a sharp one would show a double image between.
- **The radius decays exponentially.** Text only visibly changes while the blur is a few pixels
  wide, so a curve that stays high and drops at the end (smoothstep) reads as a cut. A decay spends
  the same distance on each halving, and the content eases back into focus.
- **It reaches further below the element than above**, as Material's key light casts shadows
  downwards (`haloDrop`), and blurs less of whatever the element opened from.
- **It comes into focus on its own**, strength scaling radius and opacity together, never inside the
  element's enter animation: there it would be scaled by the element's spring and cut to its bounds
  while fading.

Used by: Home, one halo around the search bar and scan button together (two side by side would each
blur over the other's button, and one follows the pair as the button changes width); the sort menu
(`HaloDropdownMenuPopup`: a popup's window ends at its content, so the halo takes room inside it;
the menu is placed where Material would place it and a tap on the halo closes it); and the search
field.

The halo needs Android 13, where Haze can vary the radius (`DocucraftBlurDefaults.isHaloSupported`).
Below that, the FAB, search bar and menu keep Material's shadows.

**Content that should be out of focus uses `Modifier.blur` with a `BlurRadiusSpec`** (Compose 1.13),
whose radius can vary across the layer: whatever is behind a sheet or dialog destination blurs as
it opens with the scrim ([navigation.md](navigation.md#modal-destinations)), and a recent
document's thumbnail blurs progressively under its title (a scanned page is mostly text, and a title
over sharp text reads as more of it).

**Below Android 12 nothing blurs**: a frosted surface falls back to its nearly opaque container
color and content stays sharp. Varying radii, such as the thumbnail's, need Android 13.

## Integrations

- **Firebase.** Analytics and Crashlytics, behind `AnalyticsHelper`
  (`core/data/di/AnalyticsModule.kt`).
- **FileProvider.** `${applicationId}.fileprovider`, for every document the app shares. It is
  `CatalogueFileProvider`, which names a document as the catalogue does, not by its file.
