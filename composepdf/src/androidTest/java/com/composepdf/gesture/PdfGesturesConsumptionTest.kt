/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.gesture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.composepdf.LongDocument
import com.composepdf.PdfViewer
import com.composepdf.PdfViewerState
import com.composepdf.rememberPdfViewerState
import com.composepdf.waitUntilLoaded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2 (`docs/pdf-engine.md`): whatever sits in the viewer's `overlay` — selection handles, a link
 * preview's buttons — must be able to keep a gesture for itself. The viewer used to start its own
 * gesture on every touch, consumed or not.
 */
@RunWith(AndroidJUnit4::class)
class PdfGesturesConsumptionTest {

    @get:Rule val rule = createComposeRule()

    private lateinit var state: PdfViewerState
    private var viewerTaps = 0
    private var overlayClicks = 0

    @Test
    fun aDragKeptByAnOverlayChildDoesNotPanTheDocument() {
        show { Box(it.pointerInput(Unit) { detectDragGestures { change, _ -> change.consume() } }) }
        val before = rule.runOnIdle { state.panY }

        rule.onNodeWithTag(OVERLAY).performTouchInput { swipeUp() }
        rule.waitForIdle()

        rule.runOnIdle { assertEquals(before, state.panY, 0.5f) }
    }

    @Test
    fun aTapKeptByAnOverlayChildDoesNotReachTheViewer() {
        show { Box(it.clickable { overlayClicks++ }) }

        rule.onNodeWithTag(OVERLAY).performClick()
        // A viewer tap is only delivered once the double-tap window has passed.
        rule.mainClock.advanceTimeBy(DOUBLE_TAP_WINDOW_MS)
        rule.waitForIdle()

        rule.runOnIdle {
            assertEquals(1, overlayClicks)
            assertEquals(0, viewerTaps)
        }
    }

    /** The control: the same swipe away from the overlay still scrolls. */
    @Test
    fun aDragElsewhereStillPansTheDocument() {
        show { Box(it.pointerInput(Unit) { detectDragGestures { change, _ -> change.consume() } }) }
        val before = rule.runOnIdle { state.panY }

        rule.onRoot().performTouchInput { swipeUp(startY = bottom * 0.9f, endY = bottom * 0.5f) }
        rule.waitForIdle()

        rule.runOnIdle { assertTrue("expected a pan", state.panY < before - 1f) }
    }

    private fun show(overlayChild: @Composable (Modifier) -> Unit) {
        rule.setContent {
            state = rememberPdfViewerState()
            PdfViewer(
                source = LongDocument,
                state = state,
                modifier = Modifier.fillMaxSize(),
                onTap = { viewerTaps++ },
                overlay = {
                    overlayChild(Modifier.size(160.dp).align(Alignment.Center).testTag(OVERLAY))
                },
            )
        }
        rule.waitUntilLoaded { state }
    }

    private companion object {
        const val OVERLAY = "overlay-child"
    }
}
