<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Home-screen widgets

The app's widgets live in `feature/docscanner/presentation/widgets`. There are two: the scan widget,
which starts a scan from the home screen, and the recent documents widget, which lists the documents
used last.

## How a widget is drawn

A widget is not drawn by the app. The launcher draws it, in its own process, from a `RemoteViews`:
the id of a layout in `res/layout` and a short list of instructions for it (set this text, open this
when pressed). So a widget's layout can only use the views the platform allows there (`FrameLayout`,
`LinearLayout`, `TextView`, `ImageView`, `ListView` and a few more), and nothing of Compose.

The app's side is an `AppWidgetProvider`, which is a broadcast receiver. The system calls it when a
widget is placed, when it is resized, after a restart and after the app is updated. The process may
be started only for that call, and has a few seconds to answer.

## Why not Glance

The widgets are written against the platform, not with Glance, for two reasons that both come from
crashes of the released app:

- **Glance draws every update in a WorkManager worker**, and it cannot be told not to. On a device
  where WorkManager cannot start (see [scanning.md](scanning.md)), every update of a widget would
  end the process, and the only way around it is a second widget that does not use Glance.
- **Glance adds its own activities, receivers and services to the app**, and one of them throws when
  it is started without the data Glance gives it. Nothing in the app can catch that.

What is given up is writing the widget as composables. What is gained is that a widget that shows
nothing changing runs no code at all between the moment it is placed and the moment it is pressed.

## Rules

- **A press opens an activity directly**, with `PendingIntent.getActivity`. A press that goes through
  a receiver first can be refused by the system as a launch from the background, and then does
  nothing.
- **A widget asks for no periodic update** (`updatePeriodMillis="0"`). One whose content changes is
  updated by the app when that content changes.
- **A receiver never lets an exception out.** It ends the process, and the widget is the one part of
  the app that runs when the user is not looking at it.
- **The receiver's class name is the widget's identity.** Renaming or moving it removes the widgets
  people have placed.
- **What a widget shows is chosen from its size, as plain logic** (`ScanWidgetLayout`,
  `RecentWidgetLayout`). From Android 12 the launcher reports every size the widget can be shown
  at, and gets views for each; before, it reports a range, and gets views for portrait and for
  landscape. `WidgetSizes` reads either and builds the views.
- **Every press is built in `WidgetIntents`.** Two pending intents that differ only in their extras
  are the same one to the system, so a press that names a document carries its uuid in the Intent's
  data as well.
- **Every word a widget shows is set by its receiver**, with `setTextViewText` and
  `setContentDescription`, and never left to `android:text` in the layout. The launcher reads a
  layout's strings in the language of the system. The app can be given a language of its own
  (Android 13 and later), and a widget that takes some of its words from each ends up in two
  languages at once. The text in the layouts is only what shows before the first update.
- **A widget is drawn again when the language changes.** Both receivers listen for
  `ACTION_LOCALE_CHANGED`, which the system also sends an app when its own language is changed.
- **A title that may not fit shrinks before it is cut** (`autoSizeTextType`, from Android 8). The
  scan widget's is two words in English and much longer in others, in a space the user can make as
  small as two cells.
- **Colors are resources, `widget_*` in `colors.xml`**: the wallpaper's on Android 12 and later
  (`values-v31`, `values-night-v31`), the app's default palette before. A widget cannot follow the
  color the user picks in the app's settings, because it is drawn from resources by the launcher.
- **An icon is tinted with `android:tint`**, and the layout tells lint so (`UseAppTint`). The
  launcher inflates a widget's layout, not AppCompat, so the `app:tint` lint asks for would be
  ignored there.
- **Corners are `@dimen/widget_corner_radius`**, which from Android 12 is the launcher's own, and
  the root of each layout is `@android:id/background`, which is what the launcher animates from when
  the widget opens the app.

## What a press asks for

A widget asks `MainActivity` with one of the Intents in
`feature/docscanner/navigation/EntryIntents.kt`: start a scan, open a document, open the search.
How each reaches its destination is in
[navigation.md](navigation.md#requests-from-outside-the-ui). The Activity is exported, so these are
not only the widgets' to send: none carries more than a document's uuid, which is only ever looked
up in the catalogue.

## Widgets that show the library

A widget is a picture the launcher keeps, so one that lists documents has to be told when the
library changes. Three pieces do that, and none of them knows what any widget looks like:

- **`ObserveWidgetDocumentsUseCase`** gives the documents a widget lists, as `WidgetDocument`: the
  library's own, used last, most recent first. It is narrower than Recents in the app. A document
  of another app is left out, because a home screen is read by anyone holding the phone and that
  name is not the library's to put there. So is one whose file is missing, because a row has no
  room to say why it would not open.
- **`KeepWidgetsCurrentUseCase`** runs for the life of the process, started by `App`. When what a
  widget lists has changed and stayed still for a moment, it asks for a refresh. Waiting matters:
  saving a scan writes the document several times over, and each refresh is the launcher drawing
  every widget again.
- **`HomeScreenWidgets`** is the port it asks. `AppWidgetHomeScreenWidgets` sends each receiver of
  a widget that shows the library the same update broadcast the system sends, so a widget is drawn
  by one piece of code whoever asks. It does nothing when no such widget is placed. The receivers
  are listed where it is bound, in `WidgetsModule`.

## The recent documents widget

`RecentDocumentsWidgetReceiver` is asked to draw by the system, when the widget is placed or resized,
and by the app, when the library changes. The drawing is `RecentDocumentsWidgetRenderer`'s: it reads
the documents, loads their previews and sends the launcher the views, which
`RecentDocumentsWidgetViews` builds from them. It reads the library, so the receiver asks the system
to wait (`goAsync`) and gives up before the few seconds a receiver is given run out, leaving the
widget as it was.

- **It does not scroll.** It shows the rows that fit its height (`RecentWidgetLayout.rowsFor`), each
  added to the layout as a view. A list that scrolls on a home screen has to be fed through a
  service the launcher binds to and asks one row at a time, differently before and after
  Android 12; rows that are part of the widget are drawn with it, the same way everywhere.
- **A row** (`widget_recents_row`) shows the first page, the name, and the page count with the day
  the document was last used. It is a date and never "2 hours ago": the widget is drawn when the
  library changes, and words that depend on the time would go on saying what was true then.
- **Previews** come from `DocumentThumbnails`, are made small and rounded with the app's image
  loader, and travel to the launcher as bitmaps. One that is not ready in time is left out, and its
  row shows a placeholder.
- **Presses**: a row opens its document, the header's buttons open the search and start a scan, and
  the title opens the app as it was left.
- **With no document to list** it says so, and the scan button is still there.
- **In the widget picker** it shows rows that name no document (`widget_recents_preview`): a picker
  cannot be given the library, and an invented name would have to be written in every language.

## The scan widget

`ScanDocumentWidgetReceiver` sends `ACTION_SCAN_DOCUMENT` to `MainActivity`; what happens from there
is in [scanning.md](scanning.md#entry-points). It has three layouts, which `ScanWidgetViews` turns
into what the launcher is sent:

| Layout | When | Shows |
|---|---|---|
| `widget_scan_small` | Narrower than two cells | The icon |
| `widget_scan_wide` | Two cells wide or more, one tall | The icon and the title |
| `widget_scan_large` | Two cells or more each way | The title, and the button in the corner |

## Tests

- **What a widget shows for a size, and what it lists, are decided in plain code and tested on the
  JVM**: `ScanWidgetLayoutTest`, `RecentWidgetLayoutTest`, `WidgetUseCasesTest`, and the requests a
  press becomes (`ScanRequestBusTest`, `LibraryEntryRequestsTest`, `EnterFromHomeTest`).
- **The views are inflated on a device as a launcher inflates them** (`WidgetViewsTest`): a layout
  that uses something a launcher refuses compiles, installs, and shows "Can't load widget" on the
  home screen. The views are built apart from the receivers (`ScanWidgetViews`,
  `RecentDocumentsWidgetViews`) so that a test can do this with no widget placed. It also applies
  an update over views already inflated, which is how rows left from the last time would show.
- **`WidgetSizesTest`** reads what a launcher says about size, in both of the ways it is said.
- **`WidgetRegistrationTest`** has the receivers' names written out, and fails when one is renamed
  or moved. It also checks that each press is a pending intent of its own.
- **They run on API 24 as well as a recent version** ([testing.md](testing.md#running)): what a
  launcher accepts in a layout, and how it reports sizes, are what changed between them.
- **What no test sees is the widget on a home screen**: its colors over a wallpaper, its text in
  the space a launcher really gives it, a press. That is checked by hand, by placing it.
