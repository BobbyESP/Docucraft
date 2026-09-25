/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.gesture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composepdf.PdfInteractionHandler
import com.composepdf.PdfSource
import com.composepdf.PdfTapEvent
import com.composepdf.PdfViewer
import com.composepdf.PdfViewerState
import com.composepdf.rememberPdfViewerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E3: a long press the caller claims hands the finger over. What the viewer does with gestures
 * nobody claims is in [PdfGesturesTest]; it must not change.
 */
@RunWith(AndroidJUnit4::class)
class PdfInteractionHandlerTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var state: PdfViewerState
    private var claims = false
    private var showViewer by mutableStateOf(true)
    private val drags = mutableListOf<PdfTapEvent>()
    private var dragEnds = 0
    private var handlerLongPresses = 0
    private var viewerLongPresses = 0
    private var taps = 0
    private var scrolledAround = Offset.Zero

    private val handler =
        object : PdfInteractionHandler {
            override fun onLongPress(event: PdfTapEvent): Boolean {
                handlerLongPresses++
                return claims
            }

            override fun onDrag(event: PdfTapEvent) {
                drags += event
            }

            override fun onDragEnd() {
                dragEnds++
            }
        }

    @Test
    fun aClaimedLongPressHandsTheDragOverAndLeavesTheDocumentStill() {
        claims = true
        show()
        val before = rule.runOnIdle { Offset(state.panX, state.panY) }

        rule.onRoot().performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MS)
            repeat(8) { moveBy(Offset(0f, 30f)) }
            up()
        }
        rule.waitForIdle()

        rule.runOnIdle {
            assertEquals(1, handlerLongPresses)
            assertEquals(
                "the handler took it; the viewer's own callback is not told",
                0,
                viewerLongPresses,
            )
            assertEquals("the document did not move", before, Offset(state.panX, state.panY))
            assertEquals("nothing around the viewer saw a scroll", Offset.Zero, scrolledAround)
            assertEquals(8, drags.size)
            assertTrue(drags.all { it.pageIndex != null && it.pagePosition != null })
            val ys = drags.map { it.pagePosition!!.y }
            assertEquals("the drag moves down the page", ys.sorted(), ys)
            assertEquals(1, dragEnds)
            assertEquals(0, taps)
        }
    }

    @Test
    fun aLongPressNobodyClaimsStillLetsTheFingerPan() {
        claims = false
        show()
        val before = rule.runOnIdle { state.panY }

        rule.onRoot().performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MS)
            repeat(8) { moveBy(Offset(0f, -30f)) }
            up()
        }
        rule.waitForIdle()

        rule.runOnIdle {
            assertEquals(1, handlerLongPresses)
            assertEquals("unclaimed, it reaches the viewer's own callback", 1, viewerLongPresses)
            assertTrue("expected the document to move, panY stayed $before", state.panY < before)
            assertEquals(0, drags.size)
            assertEquals(0, dragEnds)
        }
    }

    @Test
    fun aClaimedGestureCannotBecomeAPinch() {
        claims = true
        show()
        val before = rule.runOnIdle { state.zoom }

        rule.onRoot().performTouchInput {
            down(0, center)
            advanceEventTime(LONG_PRESS_MS)
            down(1, center + Offset(60f, 0f))
            repeat(8) { moveBy(1, Offset(40f, 0f)) }
            up(1)
            up(0)
        }
        rule.waitForIdle()

        rule.runOnIdle {
            assertEquals(before, state.zoom)
            assertEquals(1, dragEnds)
        }
    }

    @Test
    fun aClaimedGestureThatIsCancelledStillEnds() {
        claims = true
        show()

        rule.onRoot().performTouchInput {
            down(center)
            advanceEventTime(LONG_PRESS_MS)
            moveBy(Offset(0f, 30f))
        }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, dragEnds) }

        // The viewer leaves mid-drag: its gesture coroutine is cancelled.
        showViewer = false
        rule.waitForIdle()

        rule.runOnIdle { assertEquals(1, dragEnds) }
    }

    private fun show() {
        rule.setContent {
            state = rememberPdfViewerState()
            // Records any scroll the viewer passes on, as the app's bars watching it would.
            val watcher =
                object : NestedScrollConnection {
                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        scrolledAround += consumed + available
                        return Offset.Zero
                    }
                }
            Box(Modifier.fillMaxSize().nestedScroll(watcher)) {
                if (showViewer) {
                    PdfViewer(
                        source = PdfSource.Asset("fixtures/long-320-pages.pdf"),
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        onTap = { taps++ },
                        onLongPress = { viewerLongPresses++ },
                        interactionHandler = handler,
                    )
                }
            }
        }
        rule.waitUntil(timeoutMillis = 15_000) { state.isLoaded }
        rule.waitForIdle()
    }

    private companion object {
        /** Past any device's long-press timeout. */
        const val LONG_PRESS_MS = 1_000L
    }
}
