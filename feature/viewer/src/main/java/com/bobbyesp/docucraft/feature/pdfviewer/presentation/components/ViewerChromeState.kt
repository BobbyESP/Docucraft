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
import com.bobbyesp.docucraft.core.presentation.common.ScrollHeading
import com.bobbyesp.docucraft.core.presentation.common.ScrollHeadingThreshold
import com.bobbyesp.docucraft.core.presentation.common.ScrollHeadingTracker

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
class ViewerChromeState(initiallyVisible: Boolean, thresholdPx: Float) {

    var isVisible: Boolean by mutableStateOf(initiallyVisible)
        private set

    /**
     * The bars follow where the scroll is heading, not its last pixel, so a jitter cannot flicker.
     */
    private val heading = ScrollHeadingTracker(thresholdPx)

    fun toggle() {
        isVisible = !isVisible
        heading.reset()
    }

    fun show() {
        isVisible = true
        heading.reset()
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
        when (heading.onScrolled(deltaY)) {
            ScrollHeading.Forward -> isVisible = false
            ScrollHeading.Backward -> isVisible = true
            null -> Unit
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
    val thresholdPx = with(LocalDensity.current) { ScrollHeadingThreshold.toPx() }
    return rememberSaveable(saver = ViewerChromeState.saver(thresholdPx)) {
        ViewerChromeState(initiallyVisible = true, thresholdPx = thresholdPx)
    }
}
