/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.os.BundleCompat
import com.bobbyesp.docucraft.MainActivity
import com.bobbyesp.docucraft.R

/** What the scan widget sends [MainActivity] when it is pressed. */
const val ACTION_SCAN_DOCUMENT = "com.bobbyesp.docucraft.ACTION_SCAN_DOCUMENT"

/**
 * The home-screen widget that starts a scan.
 *
 * It is drawn by the launcher, from a layout and a few instructions sent as [RemoteViews], and it
 * shows nothing that changes. So this receiver only answers the system when a widget is placed or
 * resized, and the press opens [MainActivity] directly: no code of the app runs in between, and
 * nothing here depends on a background service that a device may not be able to run.
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
        // If any error occurs, the widget keeps what it was showing.
        try {
            manager.updateAppWidget(id, viewsFor(context, options))
        } catch (e: RuntimeException) {
            Log.w(TAG, "The scan widget $id could not be updated", e)
        }
    }

    private fun viewsFor(context: Context, options: Bundle): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            exactSizes(options)?.let { sizes ->
                return RemoteViews(
                    sizes.associateWith { size ->
                        layoutViews(context, ScanWidgetLayout.forSize(size.width, size.height))
                    }
                )
            }
        }
        // Before Android 12 the launcher reports a range, which is two sizes: the widget is as
        // narrow and tall as it gets in portrait, as wide and short as it gets in landscape.
        return RemoteViews(
            layoutViews(
                context,
                layoutFor(
                    widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
                    heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                ),
            ),
            layoutViews(
                context,
                layoutFor(
                    widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                    heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
                ),
            ),
        )
    }

    /** Every size the launcher may show the widget at, where it says so. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun exactSizes(options: Bundle): List<SizeF>? =
        BundleCompat.getParcelableArrayList(
                options,
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                SizeF::class.java,
            )
            ?.takeIf { it.isNotEmpty() }

    /** A launcher that reports no size gets the layout the widget is placed with. */
    private fun layoutFor(widthDp: Int, heightDp: Int): ScanWidgetLayout =
        if (widthDp <= 0 || heightDp <= 0) ScanWidgetLayout.Wide
        else ScanWidgetLayout.forSize(widthDp.toFloat(), heightDp.toFloat())

    private fun layoutViews(context: Context, layout: ScanWidgetLayout): RemoteViews {
        val layoutId =
            when (layout) {
                ScanWidgetLayout.Small -> R.layout.widget_scan_small
                ScanWidgetLayout.Wide -> R.layout.widget_scan_wide
                ScanWidgetLayout.Large -> R.layout.widget_scan_large
            }
        return RemoteViews(context.packageName, layoutId).apply {
            setOnClickPendingIntent(android.R.id.background, scanIntent(context))
        }
    }

    private fun scanIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_SCAN_DOCUMENT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val TAG = "ScanDocumentWidget"
    }
}
