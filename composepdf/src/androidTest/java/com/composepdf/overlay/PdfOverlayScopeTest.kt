/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composepdf.PdfSource
import com.composepdf.PdfViewer
import com.composepdf.PdfViewerState
import com.composepdf.rememberPdfViewerState
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E4: the overlay follows the pages in page coordinates, and moving the document redraws and
 * re-places it without recomposing it.
 */
@RunWith(AndroidJUnit4::class)
class PdfOverlayScopeTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var state: PdfViewerState
    private var compositions = 0
    private var anchoredClicks = 0
    private var draws = 0
    /** The pages of the latest draw pass and their bounds. Pages are drawn in ascending order. */
    private val lastPass = mutableMapOf<Int, Rect>()
    private var previousPage = -1

    @Test
    fun anAnchoredElementFollowsItsPointOnThePage() {
        show(anchor = Offset(0.5f, 0.25f), alignment = Alignment.Center)
        assertAnchoredAt(page = 1, Offset(0.5f, 0.25f), Alignment.Center)

        rule.runOnIdle { state.panBy(Offset(0f, -700f)) }
        rule.waitForIdle()
        assertAnchoredAt(page = 1, Offset(0.5f, 0.25f), Alignment.Center)
    }

    @Test
    fun theAlignmentPointIsWhatSitsOnTheAnchor() {
        show(anchor = Offset(0.5f, 0.25f), alignment = Alignment.TopCenter)
        assertAnchoredAt(page = 1, Offset(0.5f, 0.25f), Alignment.TopCenter)
    }

    /** What selection handles rely on: touched where it is drawn, and the pages stay still. */
    @Test
    fun anAnchoredElementIsTouchedWhereItIsShown() {
        show(anchor = Offset(0.5f, 0.25f), alignment = Alignment.Center)
        val before = rule.runOnIdle { state.panY }

        rule.onRoot().performTouchInput {
            val page = state.pageRectInViewer(1)!!
            click(Offset(page.left + 0.5f * page.width, page.top + 0.25f * page.height))
        }
        rule.waitForIdle()

        rule.runOnIdle {
            assertEquals(1, anchoredClicks)
            assertEquals(before, state.panY)
        }
    }

    @Test
    fun drawingReachesEveryVisiblePageAtItsBounds() {
        show()
        rule.runOnIdle {
            val visible = state.visiblePages.toList()
            assertTrue("more than one page on screen", visible.size > 1)
            assertEquals(visible, lastPass.keys.sorted())
            for (page in visible) assertEquals(state.pageRectInViewer(page), lastPass[page])
        }
    }

    @Test
    fun movingTheDocumentRedrawsWithoutRecomposing() {
        show()
        val composed = rule.runOnIdle { compositions }
        val drawn = rule.runOnIdle { draws }

        // Over several pages, and a zoom.
        repeat(10) {
            rule.runOnIdle { state.panBy(Offset(0f, -400f)) }
            rule.waitForIdle()
        }
        rule.runOnIdle { state.setZoom(2f) }
        rule.waitForIdle()

        rule.runOnIdle {
            assertTrue("the drawing followed", draws > drawn)
            assertEquals("the overlay was not recomposed", composed, compositions)
            for (page in state.visiblePages) assertEquals(
                state.pageRectInViewer(page),
                lastPass[page],
            )
        }
    }

    @Test
    fun hitTestAndPanBySpeakTheViewersCoordinates() {
        show()
        rule.runOnIdle {
            val page = state.pageRectInViewer(0)!!
            val event = state.hitTest(page.center)
            assertEquals(0, event.pageIndex)
            val position = checkNotNull(event.pagePosition)
            assertTrue(abs(position.x - 0.5f) < 0.01f && abs(position.y - 0.5f) < 0.01f)

            assertEquals(
                "the top of the document stops it",
                0f,
                state.panBy(Offset(0f, 300f)).y,
                0.5f,
            )
            assertEquals(-300f, state.panBy(Offset(0f, -300f)).y, 0.5f)
        }
    }

    private fun show(anchor: Offset = Offset.Zero, alignment: Alignment = Alignment.Center) {
        rule.setContent {
            state = rememberPdfViewerState()
            PdfViewer(
                source = PdfSource.Asset("fixtures/long-320-pages.pdf"),
                state = state,
                modifier = Modifier.fillMaxSize(),
                overlay = {
                    SideEffect { compositions++ }
                    DrawOnPages {
                        if (pageIndex <= previousPage) lastPass.clear()
                        previousPage = pageIndex
                        lastPass[pageIndex] = pageBounds
                        draws++
                    }
                    Box(
                        Modifier.size(20.dp)
                            .anchorTo(1, anchor, alignment)
                            .testTag("anchored")
                            .clickable { anchoredClicks++ }
                    )
                },
            )
        }
        rule.waitUntil(timeoutMillis = 15_000) { state.isLoaded }
        rule.waitForIdle()
    }

    private fun assertAnchoredAt(page: Int, position: Offset, alignment: Alignment) {
        val bounds = rule.onNodeWithTag("anchored").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle {
            val rect = state.pageRectInViewer(page)!!
            val target =
                Offset(rect.left + position.x * rect.width, rect.top + position.y * rect.height)
            val point =
                when (alignment) {
                    Alignment.TopCenter -> Offset(bounds.center.x, bounds.top)
                    else -> bounds.center
                }
            assertTrue(
                "expected $target, element at $bounds",
                (point - target).getDistance() <= 1.5f,
            )
        }
    }
}
