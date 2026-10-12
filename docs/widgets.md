<!--
  Copyright (C) 2026  Gabriel Fontán (BobbyESP)
-->

# Home-screen widgets

The app's widgets live in `feature/docscanner/presentation/widgets`. There is one: the scan widget,
which starts a scan from the home screen.

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
- **The layout is chosen from the size, as plain logic** (`ScanWidgetLayout`). From Android 12 the
  launcher reports every size the widget can be shown at, and gets a layout for each; before, it
  reports a range, and gets one layout for portrait and one for landscape.
- **Colors are resources, `widget_*` in `colors.xml`**: the wallpaper's on Android 12 and later
  (`values-v31`, `values-night-v31`), the app's default palette before. A widget cannot follow the
  color the user picks in the app's settings, because it is drawn from resources by the launcher.
- **Corners are `@dimen/widget_corner_radius`**, which from Android 12 is the launcher's own, and
  the root of each layout is `@android:id/background`, which is what the launcher animates from when
  the widget opens the app.

## The scan widget

`ScanDocumentWidgetReceiver` sends `ACTION_SCAN_DOCUMENT` to `MainActivity`; what happens from there
is in [scanning.md](scanning.md#entry-points). It has three layouts:

| Layout | When | Shows |
|---|---|---|
| `widget_scan_small` | Narrower than two cells | The icon |
| `widget_scan_wide` | Two cells wide or more, one tall | The icon and the title |
| `widget_scan_large` | Two cells or more each way | The title, and the button in the corner |
