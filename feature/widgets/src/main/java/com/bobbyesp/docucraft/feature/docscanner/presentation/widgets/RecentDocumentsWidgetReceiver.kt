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
import com.bobbyesp.docucraft.feature.widgets.presentation.RecentDocumentsWidgetRenderer
import com.bobbyesp.docucraft.feature.widgets.presentation.placedWidgets
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named

/**
 * The home-screen widget that lists the documents used last.
 *
 * Asked to draw by the system when a widget is placed or resized, and by the app when the library
 * changes (`HomeScreenWidgets`). Either way the drawing is [RecentDocumentsWidgetRenderer]'s, and
 * reads the library, so it is done off the main thread while the system is asked to wait.
 */
class RecentDocumentsWidgetReceiver : AppWidgetProvider(), KoinComponent {

    private val renderer: RecentDocumentsWidgetRenderer by inject()
    private val scope: CoroutineScope by inject(named("AppMainSupervisedScope"))

    override fun onReceive(context: Context, intent: Intent) {
        // Its rows are written in the app's language when they are drawn, so a new language is a
        // reason to draw them again.
        if (intent.action == Intent.ACTION_LOCALE_CHANGED) {
            placedWidgets(context)?.let { (_, ids) -> render(ids) }
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        render(appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        render(intArrayOf(appWidgetId))
    }

    private fun render(appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return

        // A receiver is done when it returns, and its process may go with it. This asks the system
        // to wait for the drawing: it gives a few seconds, so the drawing gives up before they end.
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                withTimeoutOrNull(RENDER_BUDGET_MILLIS.milliseconds) {
                    renderer.render(appWidgetIds)
                } ?: Log.w(TAG, "The widget was not drawn in time and keeps what it showed")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // An exception out of here ends the process. The widget keeps what it was showing.
                Log.w(TAG, "The recent documents widget could not be drawn", e)
            } finally {
                pending?.finish()
            }
        }
    }

    private companion object {
        const val TAG = "RecentDocumentsWidget"
        const val RENDER_BUDGET_MILLIS = 8_000L
    }
}
