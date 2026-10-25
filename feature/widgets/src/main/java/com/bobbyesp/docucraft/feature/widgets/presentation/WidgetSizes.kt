/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.widgets.presentation

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.os.BundleCompat

/**
 * The sizes, in dp, a launcher may show one widget at, and the views for each.
 *
 * A widget cannot measure itself: it is drawn in the launcher's process from views built here,
 * before anything is laid out. So the launcher is given one set of views per size it reports, and
 * picks the one that fits as the screen turns or the widget is resized.
 *
 * - From Android 12 it reports every size ([exact]).
 * - Before, it reports a range, which is two sizes: the widget is as narrow and tall as it gets in
 *   portrait, as wide and short as it gets in landscape.
 */
internal class WidgetSizes
private constructor(
    private val exact: List<SizeF>?,
    private val landscape: SizeF,
    private val portrait: SizeF,
) {

    /** Every size views will be asked for, to prepare what the largest of them needs. */
    val all: List<SizeF>
        get() = exact ?: listOf(landscape, portrait)

    fun views(build: (SizeF) -> RemoteViews): RemoteViews =
        if (exact != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RemoteViews(exact.associateWith(build))
        } else {
            RemoteViews(build(landscape), build(portrait))
        }

    companion object {
        /** A launcher never keeps more layouts than this for one widget. */
        private const val MOST_SIZES = 16

        /** @param fallback The size to assume for a launcher that reports none. */
        fun of(options: Bundle, fallback: SizeF): WidgetSizes {
            val exact =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BundleCompat.getParcelableArrayList(
                            options,
                            AppWidgetManager.OPTION_APPWIDGET_SIZES,
                            SizeF::class.java,
                        )
                        ?.distinct()
                        ?.take(MOST_SIZES)
                        ?.takeIf { it.isNotEmpty() }
                } else null

            fun size(widthKey: String, heightKey: String): SizeF {
                val width = options.getInt(widthKey)
                val height = options.getInt(heightKey)
                return if (width <= 0 || height <= 0) fallback
                else SizeF(width.toFloat(), height.toFloat())
            }

            return WidgetSizes(
                exact = exact,
                landscape =
                    size(
                        AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
                        AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    ),
                portrait =
                    size(
                        AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                        AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                    ),
            )
        }
    }
}

/**
 * The widgets of this receiver that are on a home screen, with what updates them. `null` when there
 * are none, or on a device with no home screen that takes widgets.
 */
internal fun AppWidgetProvider.placedWidgets(context: Context): Pair<AppWidgetManager, IntArray>? =
    try {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager?.getAppWidgetIds(ComponentName(context, javaClass))
        if (manager == null || ids == null || ids.isEmpty()) null else manager to ids
    } catch (e: RuntimeException) {
        null
    }
