/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.sign

/**
 * Whether the viewer's bars are showing. One value for both, because they are one piece of chrome:
 * reading forward hides them, scrolling back shows them, and a tap toggles them.
 *
 * The bars listen through [nestedScrollConnection], which only watches what the document scrolled
 * and never consumes any of it. The components' own scroll behaviours are no good here: the top app
 * bar's collapses by consuming the scroll first, and with bars drawn over the pages that would
 * stall the document for as long as the bar is moving.
 */
@Stable
class ViewerChromeState(initiallyVisible: Boolean, private val thresholdPx: Float) {

    var isVisible: Boolean by mutableStateOf(initiallyVisible)
        private set

    /** Distance scrolled in one direction since the last change, so a jitter does not flicker. */
    private var travel = 0f

    fun toggle() {
        isVisible = !isVisible
        travel = 0f
    }

    fun show() {
        isVisible = true
        travel = 0f
    }

    val nestedScrollConnection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                onScrolled(consumed.y)
                return Offset.Zero
            }
        }

    /** [deltaY] is what the document moved: negative while reading forward. */
    internal fun onScrolled(deltaY: Float) {
        if (deltaY == 0f) return
        if (sign(deltaY) != sign(travel)) travel = 0f
        travel += deltaY
        when {
            travel <= -thresholdPx && isVisible -> isVisible = false
            travel >= thresholdPx && !isVisible -> isVisible = true
        }
    }

    companion object {
        fun saver(thresholdPx: Float): Saver<ViewerChromeState, Boolean> =
            Saver(save = { it.isVisible }, restore = { ViewerChromeState(it, thresholdPx) })
    }
}

/** Survives rotation and process death, like the rest of what the reader sees. */
@Composable
fun rememberViewerChromeState(): ViewerChromeState {
    val thresholdPx = with(LocalDensity.current) { HideThreshold.toPx() }
    return rememberSaveable(saver = ViewerChromeState.saver(thresholdPx)) {
        ViewerChromeState(initiallyVisible = true, thresholdPx = thresholdPx)
    }
}

/** How far the document must travel one way before the bars follow. */
private val HideThreshold = 24.dp
