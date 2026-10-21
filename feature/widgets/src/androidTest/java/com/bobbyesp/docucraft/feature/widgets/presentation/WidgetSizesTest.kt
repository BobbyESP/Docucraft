/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.widgets.presentation

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a launcher says about a widget's size, read on the device: the two ways of saying it belong
 * to different versions of Android, and a launcher may say nothing at all.
 */
@RunWith(AndroidJUnit4::class)
class WidgetSizesTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val fallback = SizeF(180f, 60f)

    @Test
    fun aLauncherThatReportsNothingGetsTheSizeTheWidgetIsPlacedAt() {
        assertEquals(listOf(fallback, fallback), WidgetSizes.of(Bundle(), fallback).all)
    }

    @Test
    fun aRangeIsTheWidgetInLandscapeAndInPortrait() {
        val sizes =
            WidgetSizes.of(
                range(minWidth = 110, maxWidth = 240, minHeight = 50, maxHeight = 130),
                fallback,
            )

        // Wide and short when the screen is turned, narrow and tall when it is upright.
        assertEquals(listOf(SizeF(240f, 50f), SizeF(110f, 130f)), sizes.all)
    }

    @Test
    @SdkSuppress(minSdkVersion = 31)
    fun everySizeReportedIsUsedOnceAndTheRangeIsNot() {
        val options = range(minWidth = 110, maxWidth = 240, minHeight = 50, maxHeight = 130)
        val reported = arrayListOf(SizeF(120f, 120f), SizeF(250f, 60f), SizeF(120f, 120f))
        options.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, reported)

        assertEquals(
            listOf(SizeF(120f, 120f), SizeF(250f, 60f)),
            WidgetSizes.of(options, fallback).all,
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = 31)
    fun noMoreSizesThanALauncherKeepsLayoutsFor() {
        val options = Bundle()
        val reported = ArrayList((1..40).map { SizeF(100f + it, 100f) })
        options.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, reported)

        // More than sixteen and the system refuses the whole update.
        assertEquals(reported.take(16), WidgetSizes.of(options, fallback).all)
    }

    @Test
    fun theViewsForEverySizeInflateAsOne() {
        val options = range(minWidth = 60, maxWidth = 240, minHeight = 50, maxHeight = 240)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            options.putParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(SizeF(60f, 60f), SizeF(240f, 60f), SizeF(240f, 240f)),
            )
        }
        val views =
            WidgetSizes.of(options, fallback).views { size ->
                ScanWidgetViews.of(context, ScanWidgetLayout.forSize(size.width, size.height))
            }

        lateinit var root: View
        instrumentation.runOnMainSync { root = views.apply(context, FrameLayout(context)) }

        assertEquals(android.R.id.background, root.id)
    }

    private fun range(minWidth: Int, maxWidth: Int, minHeight: Int, maxHeight: Int) =
        Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minWidth)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxWidth)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minHeight)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxHeight)
        }
}
