<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Home-screen widgets

The widgets live in `:feature:widgets`, package `feature.widgets`; their two receivers keep the
package they were placed with (`feature.docscanner.presentation.widgets`). There are two: the scan
widget, which starts a scan from the home screen, and the recent documents widget, which lists the
documents used last.

## How a widget is drawn

The launcher draws a widget in its own process from a `RemoteViews`: the id of a layout in
`res/layout` plus a short list of instructions (set this text, open this when pressed). So a layout
can only use the views the platform allows there (`FrameLayout`, `LinearLayout`, `TextView`,
`ImageView`, `ListView` and a few more), and nothing of Compose.

The app's side is an `AppWidgetProvider`, a broadcast receiver. The system calls it when a widget is
placed or resized, after a restart and after an app update. The process may start only for that
call, and has a few seconds to answer.

## Why not Glance

The widgets use the platform, not Glance, for two reasons that both come from crashes of the
released app:
- **Glance draws every update in a WorkManager worker** and cannot be told not to. Where WorkManager
  cannot start (see [scanning.md](scanning.md)), every update would end the process.
- **Glance adds its own activities, receivers and services**, and one throws when started without
  the data Glance gives it. Nothing in the app can catch that.

The cost is not writing widgets as composables. The gain is that a widget showing nothing that
changes runs no code between being placed and being pressed.

## Rules

- **A press opens an activity directly** (`PendingIntent.getActivity`). A press through a receiver
  can be refused by the system as a background launch, and then does nothing.
- **No periodic update** (`updatePeriodMillis="0"`). A widget whose content changes is updated by
  the app when it does.
- **A receiver never lets an exception out**: it ends the process, and the widget is the one part of
  the app that runs when the user is not looking.
- **The receiver's class name is the widget's identity.** Renaming or moving it removes the widgets
  people placed.
- **What a widget shows is chosen from its size, in plain logic** (`ScanWidgetLayout`,
  `RecentWidgetLayout`). From Android 12 the launcher reports every size the widget can be shown at
  and gets views for each; before, it reports a range and gets portrait and landscape views.
  `WidgetSizes` reads either and builds the views.
- **Every press is built in `WidgetIntents`.** Two pending intents that differ only in their extras
  are the same to the system, so a press naming a document also carries its uuid in the Intent's
  data.
- **Every word is set by the receiver** (`setTextViewText`, `setContentDescription`), never left to
  `android:text`. The launcher reads a layout's strings in the system language, while the app can
  have its own (Android 13+); mixing them would show two languages at once. Layout text is only what
  shows before the first update.
- **A language change redraws the widgets.** Both receivers listen for `ACTION_LOCALE_CHANGED`,
  which the system also sends when the app's own language changes.
- **A title that may not fit shrinks before it is cut** (`autoSizeTextType`, Android 8+). The scan
  widget's is two words in English and much longer elsewhere, in a space the user can make two cells
  wide.
- **Colors are resources, `widget_*` in `colors.xml`**: the wallpaper's on Android 12+
  (`values-v31`, `values-night-v31`), the app's default palette before. A widget cannot follow the
  color picked in settings, since the launcher draws it from resources.
- **An icon is tinted with `android:tint`**, and the layout tells lint (`UseAppTint`): the launcher
  inflates the layout, not AppCompat, so the `app:tint` lint asks for would be ignored.
- **Corners are `@dimen/widget_corner_radius`** (the launcher's own from Android 12), and each
  layout's root is `@android:id/background`, which the launcher animates from when the widget opens
  the app.

## What a press asks for

A widget asks `MainActivity` with one of the Intents in `EntryIntents.kt` (`:feature:widgets`): start
a scan, open a document, open the search. How each reaches its destination is in
[navigation.md](navigation.md#requests-from-outside-the-ui). The activity is exported, so these are
not only the widgets' to send: none carries more than a document's uuid, which is only looked up in
the catalogue.

## Widgets that show the library

A widget is a picture the launcher keeps, so one that lists documents must be told when the library
changes. Three pieces do it, and none knows what a widget looks like:
- **`ObserveWidgetDocumentsUseCase`** gives the documents a widget lists as `WidgetDocument`: the
  library's own, used last, most recent first. It is narrower than Recents: another app's document
  is left out (a home screen is read by anyone holding the phone, and that name is not the
  library's to show), and so is one whose file is missing (a row has no room to say why it would
  not open).
- **`KeepWidgetsCurrentUseCase`**, started by `App` for the life of the process, asks for a refresh
  when what a widget lists has changed and stayed still for a moment. Waiting matters: saving a scan
  writes the document several times, and each refresh is the launcher redrawing every widget.
- **`HomeScreenWidgets`** is the port it asks. `AppWidgetHomeScreenWidgets` sends each receiver of a
  library widget the same update broadcast the system sends, so one piece of code draws a widget
  whoever asks. It does nothing when no such widget is placed. The receivers are listed where it is
  bound, in `WidgetsModule`.

## The recent documents widget

`RecentDocumentsWidgetReceiver` is asked to draw by the system (placed, resized) and by the app
(library changed). `RecentDocumentsWidgetRenderer` reads the documents, loads their previews and
sends the launcher the views that `RecentDocumentsWidgetViews` builds. Because it reads the library,
the receiver asks the system to wait (`goAsync`) and gives up before the receiver's few seconds run
out, leaving the widget as it was.

- **It does not scroll.** It shows the rows that fit its height (`RecentWidgetLayout.rowsFor`), each
  added as a view. A scrolling list needs a service the launcher binds to and queries one row at a
  time, differently before and after Android 12; rows that are part of the widget draw the same way
  everywhere.
- **A row** (`widget_recents_row`) shows the first page, the name, and the page count with the day
  the document was last used. It is a date, never "2 hours ago": the widget is drawn when the
  library changes, so time-relative words would go on saying what was true then.
- **Previews** come from `DocumentThumbnails`, are made small and rounded with the app's image
  loader, and travel as bitmaps. One not ready in time is left out and its row shows a placeholder.
- **Presses**: a row opens its document, the header's buttons open the search and start a scan, and
  the title opens the app as it was left.
- **With no document to list** it says so, and the scan button remains.
- **In the widget picker** it shows rows naming no document (`widget_recents_preview`): a picker
  cannot be given the library, and an invented name would need writing in every language.

## The scan widget

`ScanDocumentWidgetReceiver` sends `ACTION_SCAN_DOCUMENT` to `MainActivity`; the rest is in
[scanning.md](scanning.md#entry-points). Three layouts, which `ScanWidgetViews` turns into what the
launcher gets:

| Layout | When | Shows |
|---|---|---|
| `widget_scan_small` | Narrower than two cells | The icon |
| `widget_scan_wide` | Two cells wide or more, one tall | The icon and the title |
| `widget_scan_large` | Two cells or more each way | The title, and the button in the corner |

## Tests

- **What a widget shows for a size, and what it lists, is plain code tested on the JVM**:
  `ScanWidgetLayoutTest`, `RecentWidgetLayoutTest`, `WidgetUseCasesTest`, and the requests a press
  becomes (`ScanRequestBusTest`, `LibraryEntryRequestsTest`, `EnterFromHomeTest`).
- **The views are inflated on a device as a launcher inflates them** (`WidgetViewsTest`): a layout
  using something a launcher refuses compiles, installs, and shows "Can't load widget". The views
  are built apart from the receivers (`ScanWidgetViews`, `RecentDocumentsWidgetViews`) so a test can
  do this with no widget placed. It also applies an update over views already inflated, which is how
  rows left from the last time would show.
- **`WidgetSizesTest`** reads what a launcher says about size, in both ways it is said.
- **`WidgetRegistrationTest`** has the receivers' names written out and fails when one is renamed or
  moved; it also checks that each press is a pending intent of its own.
- **They run on API 24 as well as a recent version** ([testing.md](testing.md#running)): what a
  launcher accepts in a layout and how it reports sizes are what changed between them.
- **No test sees the widget on a home screen**: its colors over a wallpaper, its text in the space a
  launcher really gives it, a press. That is checked by hand, by placing it.
