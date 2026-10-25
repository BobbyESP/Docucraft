/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.widgets.presentation

import android.appwidget.AppWidgetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/** What the system knows the widgets by: the receivers it lists, and the presses it keeps. */
@RunWith(AndroidJUnit4::class)
class WidgetRegistrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theWidgetsAreTheOnesPeopleHavePlaced() {
        val widgets =
            AppWidgetManager.getInstance(context).installedProviders.filter {
                it.provider.packageName == context.packageName
            }

        // Written out, not read from the classes: the system knows a placed widget by this name,
        // so a receiver that is renamed or moved takes every widget of its kind off the home
        // screens it is on. This fails when that happens.
        assertEquals(
            setOf(
                "com.bobbyesp.docucraft.feature.docscanner.presentation.widgets.ScanDocumentWidgetReceiver",
                "com.bobbyesp.docucraft.feature.docscanner.presentation.widgets.RecentDocumentsWidgetReceiver",
            ),
            widgets.map { it.provider.className }.toSet(),
        )
        // None wakes the app on a timer: one whose content changes is drawn when it changes.
        widgets.forEach { assertEquals(it.provider.className, 0, it.updatePeriodMillis) }
    }

    @Test
    fun eachDocumentHasAPressOfItsOwn() {
        // The system keeps one pending intent for all that differ only in their extras, and then
        // every row opens the same document.
        assertNotEquals(
            WidgetIntents.openDocument(context, "a"),
            WidgetIntents.openDocument(context, "b"),
        )
        assertEquals(
            WidgetIntents.openDocument(context, "a"),
            WidgetIntents.openDocument(context, "a"),
        )
    }

    @Test
    fun eachButtonHasAPressOfItsOwn() {
        val presses =
            listOf(
                WidgetIntents.scan(context),
                WidgetIntents.search(context),
                WidgetIntents.openApp(context),
                WidgetIntents.openDocument(context, "a"),
            )

        assertEquals(presses.size, presses.toSet().size)
    }
}
