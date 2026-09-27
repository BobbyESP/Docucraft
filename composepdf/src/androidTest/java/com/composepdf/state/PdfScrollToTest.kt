/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.state

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composepdf.PdfLayoutSpec
import com.composepdf.PdfSource
import com.composepdf.PdfViewer
import com.composepdf.PdfViewerState
import com.composepdf.rememberPdfViewerState
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E5: scrolling to a point on a page, as an internal link will. The point lands at the start of the
 * content area, below the top padding a bar would take.
 */
@RunWith(AndroidJUnit4::class)
class PdfScrollToTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var state: PdfViewerState
    private lateinit var scope: CoroutineScope
    private var topPadding = 0f

    @Test
    fun aPointOnAPageLandsAtTheStartOfTheContentArea() {
        show()

        scrollTo(150, Offset(0.5f, 0.4f))

        assertAtContentStart(page = 150, y = 0.4f)
    }

    @Test
    fun withoutAPointThePageStartsThere() {
        show()

        scrollTo(42, position = null)

        assertAtContentStart(page = 42, y = 0f)
    }

    @Test
    fun theLastPageStopsAtTheEndOfTheDocument() {
        show()

        scrollTo(319, Offset(0.5f, 0.9f))

        rule.runOnIdle {
            // It cannot reach the top: the document ends first. It gets as far as it can.
            val before = state.panY
            state.panBy(Offset(0f, -500f))
            assertEquals(before, state.panY, 0.5f)
            assertEquals(319, state.currentPage)
        }
    }

    private fun scrollTo(page: Int, position: Offset?) {
        rule.runOnIdle { scope.launch { state.animateScrollTo(page, position) } }
        rule.waitForIdle()
    }

    private fun assertAtContentStart(page: Int, y: Float) {
        rule.runOnIdle {
            val rect = state.pageRectInViewer(page)!!
            val event = state.hitTest(Offset(rect.center.x, topPadding + 1f))
            assertEquals(page, event.pageIndex)
            val landed = event.pagePosition!!.y
            assertTrue("expected $y at the content start, found $landed", abs(landed - y) < 0.01f)
        }
    }

    private fun show() {
        rule.setContent {
            state = rememberPdfViewerState()
            scope = rememberCoroutineScope()
            PdfViewer(
                source = PdfSource.Asset("fixtures/long-320-pages.pdf"),
                state = state,
                layout = PdfLayoutSpec(contentPadding = PaddingValues(top = TopBar)),
                modifier = Modifier.fillMaxSize(),
            )
        }
        rule.waitUntil(timeoutMillis = 15_000) { state.isLoaded }
        rule.waitForIdle()
        topPadding = with(rule.density) { TopBar.toPx() }
    }

    private companion object {
        val TopBar = 64.dp
    }
}
