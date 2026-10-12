/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews

/**
 * The home-screen widget that starts a scan.
 *
 * It is drawn by the launcher, from a layout and a few instructions sent as [RemoteViews], and it
 * shows nothing that changes. So this receiver only answers the system when a widget is placed or
 * resized, and the press opens the app directly: no code of the app runs in between, and nothing
 * here depends on a background service that a device may not be able to run.
 */
class ScanDocumentWidgetReceiver : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        // The widget names what it does, and has to say it again in the new language.
        if (intent.action == Intent.ACTION_LOCALE_CHANGED) {
            placedWidgets(context)?.let { (manager, ids) -> onUpdate(context, manager, ids) }
        } else {
            super.onReceive(context, intent)
        }
    }

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
                    ScanWidgetViews.of(context, ScanWidgetLayout.forSize(size.width, size.height))
                }
            manager.updateAppWidget(id, views)
        } catch (e: RuntimeException) {
            Log.w(TAG, "The scan widget $id could not be updated", e)
        }
    }

    private companion object {
        const val TAG = "ScanDocumentWidget"

        /** Two cells by one, which is how the widget is placed: the row layout. */
        val PLACED_SIZE = SizeF(180f, 60f)
    }
}
