/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.state

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composepdf.LongDocument
import com.composepdf.PdfReadingPosition
import com.composepdf.PdfViewer
import com.composepdf.PdfViewerState
import com.composepdf.rememberPdfViewerState
import com.composepdf.waitUntilLoaded
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reading position a host can keep: read from the state, and handed back to open a document
 * where it was left (`docs/pdf-engine.md`). The engine keeps it across a recreation by itself; this
 * is the same position for a host that wants to keep it for longer.
 */
@RunWith(AndroidJUnit4::class)
class PdfReadingPositionTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun aDocumentOpensAtThePositionItIsGiven() {
        val state = open(initialPosition = PdfReadingPosition(pageIndex = 40, fraction = 0.5f))

        rule.runOnIdle {
            assertEquals(40, state.currentPage)
            assertPosition(PdfReadingPosition(40, 0.5f), state.readingPosition)
        }
    }

    @Test
    fun theReadingPositionFollowsTheReader() {
        val state = open()

        rule.runOnIdle { state.scrollToPage(12) }

        // A page brought to the centre of the content area is read at its middle.
        rule.runOnIdle { assertPosition(PdfReadingPosition(12, 0.5f), state.readingPosition) }
    }

    // What a host does: read where the reader is, and open the document there another day.
    @Test
    fun aPositionReadFromOneViewerOpensAnotherOnTheSameLine() {
        val first = open()
        rule.runOnIdle {
            first.scrollToPage(25)
            first.panBy(Offset(0f, -137f))
        }
        val left = rule.runOnIdle { first.readingPosition }

        val second = open(initialPosition = left)

        rule.runOnIdle {
            assertEquals(left.pageIndex, second.currentPage)
            assertPosition(left, second.readingPosition)
        }
    }

    // The document can have changed since the position was kept.
    @Test
    fun aPositionPastTheEndOpensOnTheLastPage() {
        val state = open(initialPosition = PdfReadingPosition(pageIndex = 5_000, fraction = 0.3f))

        rule.runOnIdle {
            assertEquals(state.pageCount - 1, state.currentPage)
            assertEquals(state.pageCount - 1, state.readingPosition.pageIndex)
        }
    }

    @Test
    fun aPositionThatIsNotOneOpensAtTheStart() {
        val state = open(initialPosition = PdfReadingPosition(pageIndex = -3, fraction = Float.NaN))

        rule.runOnIdle {
            assertEquals(0, state.currentPage)
            assertEquals(0, state.readingPosition.pageIndex)
        }
    }

    // A restored state comes back where the reader was, not where the host first opened it.
    @Test
    fun aRestoredStateKeepsWhereTheReaderWasOverWhereItStarted() {
        val restoration = StateRestorationTester(rule)
        lateinit var state: PdfViewerState
        restoration.setContent {
            state = rememberPdfViewerState(initialPosition = PdfReadingPosition(40, 0.5f))
            PdfViewer(source = LongDocument, state = state, modifier = Modifier.fillMaxSize())
        }
        rule.waitUntilLoaded { state }
        rule.runOnIdle { state.scrollToPage(7) }

        restoration.emulateSavedInstanceStateRestore()
        rule.waitUntilLoaded { state }

        rule.runOnIdle { assertEquals(7, state.currentPage) }
    }

    /** A viewer over the long document, laid out. Each call replaces the one before. */
    private fun open(initialPosition: PdfReadingPosition? = null): PdfViewerState {
        val state = PdfViewerState(initialPosition = initialPosition)
        current = state
        if (!contentIsSet) {
            contentIsSet = true
            rule.setContent {
                // Keyed by the state, so a second viewer is a new one and not the first again.
                key(current) {
                    PdfViewer(
                        source = LongDocument,
                        state = current,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        rule.waitUntilLoaded { state }
        return state
    }

    private var contentIsSet = false
    private var current by mutableStateOf(PdfViewerState())

    private fun assertPosition(expected: PdfReadingPosition, actual: PdfReadingPosition) {
        assertEquals(expected.pageIndex, actual.pageIndex)
        assertEquals(expected.fraction, actual.fraction, FRACTION_TOLERANCE)
    }

    private companion object {
        // A pan is clamped to whole pixels of a page some two thousand tall.
        const val FRACTION_TOLERANCE = 0.01f
    }
}
