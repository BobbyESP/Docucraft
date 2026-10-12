/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import com.bobbyesp.docucraft.R

/**
 * The home-screen widget that starts a scan.
 *
 * It is drawn by the launcher, from a layout and a few instructions sent as [RemoteViews], and it
 * shows nothing that changes. So this receiver only answers the system when a widget is placed or
 * resized, and the press opens the app directly: no code of the app runs in between, and nothing
 * here depends on a background service that a device may not be able to run.
 */
class ScanDocumentWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            render(context, appWidgetManager, id, appWidgetManager.getAppWidgetOptions(id))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        render(context, appWidgetManager, appWidgetId, newOptions)
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        // An exception out of a receiver ends the process, and the launcher can refuse an update
        // for reasons of its own. The widget then keeps what it was showing.
        try {
            val views =
                WidgetSizes.of(options, fallback = PLACED_SIZE).views { size ->
                    layoutViews(context, ScanWidgetLayout.forSize(size.width, size.height))
                }
            manager.updateAppWidget(id, views)
        } catch (e: RuntimeException) {
            Log.w(TAG, "The scan widget $id could not be updated", e)
        }
    }

    private fun layoutViews(context: Context, layout: ScanWidgetLayout): RemoteViews {
        val layoutId =
            when (layout) {
                ScanWidgetLayout.Small -> R.layout.widget_scan_small
                ScanWidgetLayout.Wide -> R.layout.widget_scan_wide
                ScanWidgetLayout.Large -> R.layout.widget_scan_large
            }
        return RemoteViews(context.packageName, layoutId).apply {
            setOnClickPendingIntent(android.R.id.background, WidgetIntents.scan(context))
        }
    }

    private companion object {
        const val TAG = "ScanDocumentWidget"

        /** Two cells by one, which is how the widget is placed: the row layout. */
        val PLACED_SIZE = SizeF(180f, 60f)
    }
}
