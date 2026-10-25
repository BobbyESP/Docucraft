/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.widgets.domain

import com.bobbyesp.docucraft.library.domain.model.DocumentAvailability
import com.bobbyesp.docucraft.library.domain.model.RecentDocument
import com.bobbyesp.docucraft.library.testing.FakeDocumentActivityRepository
import com.bobbyesp.docucraft.library.testing.testDocument
import com.bobbyesp.docucraft.library.testing.testLinkedDocument
import com.bobbyesp.docucraft.library.testing.testRecent
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A widget is on the home screen, where anyone holding the phone reads it and nothing explains
 * itself. So what it lists is narrower than Recents, and it is drawn again only when what it lists
 * has changed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetUseCasesTest {

    private fun recents(vararg recents: RecentDocument) =
        FakeDocumentActivityRepository(recents.toList())

    private class CountingWidgets : HomeScreenWidgets {
        var refreshes = 0

        override fun refresh() {
            refreshes++
        }
    }

    // ---------------- what a widget lists ----------------

    @Test
    fun `the library's documents are listed in the order of Recents`() = runTest {
        val activity = recents(testRecent(testDocument("b")), testRecent(testDocument("a")))

        val listed = ObserveWidgetDocumentsUseCase(activity)(limit = 5).first()

        assertEquals(listOf("b", "a"), listed.map { it.uuid })
    }

    @Test
    fun `a document is listed as it is named, with when it was last used`() = runTest {
        val document = testDocument("a", title = "Invoice", pageCount = 3)
        val activity = recents(testRecent(document, lastOpenedAtEpochMillis = 9_000L))

        val listed = ObserveWidgetDocumentsUseCase(activity)(limit = 5).first().single()

        assertEquals("Invoice", listed.name)
        assertEquals(3, listed.pageCount)
        assertEquals(9_000L, listed.lastUsedAtEpochMillis)
        assertEquals(document.thumbnail, listed.thumbnail)
    }

    @Test
    fun `a document never opened is as recent as when it was saved`() = runTest {
        val activity = recents(testRecent(testDocument("a", createdAtEpochMillis = 4_000L)))

        val listed = ObserveWidgetDocumentsUseCase(activity)(limit = 5).first().single()

        assertEquals(4_000L, listed.lastUsedAtEpochMillis)
    }

    /** Its name is not the library's to put on a home screen. */
    @Test
    fun `a document of another app is not listed`() = runTest {
        val activity = recents(testRecent(testLinkedDocument("x")), testRecent(testDocument("a")))

        val listed = ObserveWidgetDocumentsUseCase(activity)(limit = 5).first()

        assertEquals(listOf("a"), listed.map { it.uuid })
    }

    @Test
    fun `a document whose file is missing is not listed`() = runTest {
        val activity =
            recents(
                testRecent(testDocument("lost"), availability = DocumentAvailability.NOT_FOUND),
                testRecent(testDocument("a")),
            )

        val listed = ObserveWidgetDocumentsUseCase(activity)(limit = 5).first()

        assertEquals(listOf("a"), listed.map { it.uuid })
    }

    /** Recents is cut to a number before it is filtered here, and must not be cut too short. */
    @Test
    fun `documents of other apps do not take the places of the library's`() = runTest {
        val others = (1..6).map { testRecent(testLinkedDocument("x$it")) }
        val own = listOf(testRecent(testDocument("a")), testRecent(testDocument("b")))
        val activity = FakeDocumentActivityRepository(others + own)

        val listed = ObserveWidgetDocumentsUseCase(activity)(limit = 2).first()

        assertEquals(listOf("a", "b"), listed.map { it.uuid })
    }

    @Test
    fun `no more are listed than the widget asked for`() = runTest {
        val activity =
            FakeDocumentActivityRepository((1..9).map { testRecent(testDocument("d$it")) })

        val listed = ObserveWidgetDocumentsUseCase(activity)(limit = 3).first()

        assertEquals(listOf("d1", "d2", "d3"), listed.map { it.uuid })
    }

    // ---------------- keeping widgets current ----------------

    @Test
    fun `the widgets catch up when the app starts`() = runTest {
        val widgets = CountingWidgets()
        val keep = KeepWidgetsCurrentUseCase(ObserveWidgetDocumentsUseCase(recents()), widgets)

        val running = launch { keep(settle = 100.milliseconds) }
        advanceTimeBy(101.milliseconds)

        assertEquals(1, widgets.refreshes)
        running.cancel()
    }

    @Test
    fun `a change to what is listed refreshes the widgets`() = runTest {
        val widgets = CountingWidgets()
        val activity = recents(testRecent(testDocument("a")))
        val keep = KeepWidgetsCurrentUseCase(ObserveWidgetDocumentsUseCase(activity), widgets)
        val running = launch { keep(settle = 100.milliseconds) }
        advanceTimeBy(101.milliseconds)

        activity.recents.value =
            listOf(testRecent(testDocument("b")), testRecent(testDocument("a")))
        advanceTimeBy(101.milliseconds)

        assertEquals(2, widgets.refreshes)
        running.cancel()
    }

    /** Saving a scan writes the document several times over in a moment. */
    @Test
    fun `changes in quick succession are one refresh`() = runTest {
        val widgets = CountingWidgets()
        val activity = recents()
        val keep = KeepWidgetsCurrentUseCase(ObserveWidgetDocumentsUseCase(activity), widgets)
        val running = launch { keep(settle = 100.milliseconds) }
        advanceTimeBy(101.milliseconds)

        activity.recents.value = listOf(testRecent(testDocument("a")))
        advanceTimeBy(50.milliseconds)
        activity.recents.value = listOf(testRecent(testDocument("a", pageCount = 4)))
        advanceTimeBy(50.milliseconds)
        activity.recents.value = listOf(testRecent(testDocument("a", pageCount = 4, title = "T")))
        advanceTimeBy(101.milliseconds)

        assertEquals(2, widgets.refreshes)
        running.cancel()
    }

    /** A document of another app being opened moves Recents, and nothing a widget shows. */
    @Test
    fun `a change to something the widgets do not show refreshes nothing`() = runTest {
        val widgets = CountingWidgets()
        val activity = recents(testRecent(testDocument("a")))
        val keep = KeepWidgetsCurrentUseCase(ObserveWidgetDocumentsUseCase(activity), widgets)
        val running = launch { keep(settle = 100.milliseconds) }
        advanceTimeBy(101.milliseconds)

        activity.recents.value =
            listOf(testRecent(testLinkedDocument("x")), testRecent(testDocument("a")))
        advanceTimeBy(101.milliseconds)
        runCurrent()

        assertEquals(1, widgets.refreshes)
        running.cancel()
    }
}
