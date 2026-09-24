/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.gesture

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composepdf.PdfSource
import com.composepdf.PdfViewer
import com.composepdf.PdfViewerState
import com.composepdf.rememberPdfViewerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The viewer's own gestures, with nothing in the overlay. A regression net for every step that
 * touches `PdfGestures` (b2, c3, d2 in `docs/architecture/08-pdfviewer-migration-plan.md`); the
 * behaviour with overlay children is in [PdfGesturesConsumptionTest].
 */
@RunWith(AndroidJUnit4::class)
class PdfGesturesTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var state: PdfViewerState
    private var taps = 0
    private var longPresses = 0

    @Test
    fun aTapIsDeliveredOnceTheDoubleTapWindowPasses() {
        show()

        rule.onRoot().performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(DOUBLE_TAP_WINDOW_MS)
        rule.waitForIdle()

        rule.runOnIdle { assertEquals(1, taps) }
    }

    @Test
    fun aDoubleTapZoomsIn() {
        show()
        val before = rule.runOnIdle { state.zoom }

        rule.onRoot().performTouchInput { doubleClick(center) }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        rule.waitForIdle()

        rule.runOnIdle {
            assertTrue("expected zoom > $before, was ${state.zoom}", state.zoom > before * 1.2f)
            assertEquals("a double tap is not a tap", 0, taps)
        }
    }

    @Test
    fun aPinchOutZoomsIn() {
        show()
        val before = rule.runOnIdle { state.zoom }

        rule.onRoot().performTouchInput {
            pinch(
                start0 = center - Offset(60f, 0f),
                end0 = center - Offset(300f, 0f),
                start1 = center + Offset(60f, 0f),
                end1 = center + Offset(300f, 0f),
            )
        }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        rule.waitForIdle()

        rule.runOnIdle {
            assertTrue("expected zoom > $before, was ${state.zoom}", state.zoom > before * 1.5f)
        }
    }

    /** Double tap and hold, then drag down: one-handed zoom. */
    @Test
    fun quickScaleZoomsInWhenDraggingDown() {
        show()
        val before = rule.runOnIdle { state.zoom }

        rule.onRoot().performTouchInput {
            down(center)
            up()
            advanceEventTime(80)
            down(center)
            repeat(10) { moveBy(Offset(0f, 40f)) }
            up()
        }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        rule.waitForIdle()

        rule.runOnIdle {
            assertTrue("expected zoom > $before, was ${state.zoom}", state.zoom > before * 1.5f)
        }
    }

    @Test
    fun aLongPressIsDelivered() {
        show()

        rule.onRoot().performTouchInput { longClick(center) }
        rule.waitForIdle()

        rule.runOnIdle {
            assertEquals(1, longPresses)
            assertEquals("a long press is not a tap", 0, taps)
        }
    }

    private fun show() {
        rule.setContent {
            state = rememberPdfViewerState()
            PdfViewer(
                source = PdfSource.Asset("fixtures/long-320-pages.pdf"),
                state = state,
                modifier = Modifier.fillMaxSize(),
                onTap = { taps++ },
                onLongPress = { longPresses++ },
            )
        }
        rule.waitUntil(timeoutMillis = 15_000) { state.isLoaded }
        rule.waitForIdle()
    }

    private companion object {
        const val DOUBLE_TAP_WINDOW_MS = 1_000L
        const val SETTLE_MS = 2_000L
    }
}
