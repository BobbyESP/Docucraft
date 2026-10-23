/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.widgets.presentation

import android.content.Context
import android.widget.RemoteViews
import com.bobbyesp.docucraft.feature.widgets.R

/**
 * What the scan widget sends the launcher for one of its layouts.
 *
 * Apart from the receiver so that it can be inflated in a test as a launcher would, which is the
 * only way to learn that a layout uses something a launcher refuses.
 */
internal object ScanWidgetViews {

    fun of(context: Context, layout: ScanWidgetLayout): RemoteViews {
        val layoutId =
            when (layout) {
                ScanWidgetLayout.Small -> R.layout.widget_scan_small
                ScanWidgetLayout.Wide -> R.layout.widget_scan_wide
                ScanWidgetLayout.Large -> R.layout.widget_scan_large
            }
        // Said from here, and not left to the layout: the launcher reads a layout's strings in the
        // language of the system, which is not the app's when the user has given it its own.
        val title = context.getString(R.string.widget_scan_title)
        return RemoteViews(context.packageName, layoutId).apply {
            if (layout == ScanWidgetLayout.Small) {
                setContentDescription(android.R.id.background, title)
            } else {
                setTextViewText(R.id.widget_scan_title, title)
            }
            setOnClickPendingIntent(android.R.id.background, WidgetIntents.scan(context))
        }
    }
}
