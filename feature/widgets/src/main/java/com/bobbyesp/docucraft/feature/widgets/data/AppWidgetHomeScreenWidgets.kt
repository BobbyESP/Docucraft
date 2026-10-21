/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.widgets.data

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bobbyesp.docucraft.feature.widgets.domain.HomeScreenWidgets

/**
 * Refreshes widgets by asking each of their receivers for an update, the way the system does when a
 * widget is placed. So a widget is drawn by one piece of code, its receiver, whoever asks.
 *
 * @param libraryWidgets The receivers of the widgets that show the library. A widget that shows
 *   nothing of it, like the one that starts a scan, is not one of them.
 */
class AppWidgetHomeScreenWidgets(
    private val context: Context,
    private val libraryWidgets: List<ComponentName>,
) : HomeScreenWidgets {

    override fun refresh() {
        try {
            val manager = AppWidgetManager.getInstance(context) ?: return
            libraryWidgets.forEach { receiver ->
                val placed = manager.getAppWidgetIds(receiver)
                if (placed.isEmpty()) return@forEach

                context.sendBroadcast(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setComponent(receiver)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, placed)
                )
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "The widgets could not be refreshed", e)
        }
    }

    private companion object {
        const val TAG = "HomeScreenWidgets"
    }
}
