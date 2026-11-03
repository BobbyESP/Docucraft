/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged

/**
 * A bar of the viewer that fades and slides in from the top or bottom edge.
 *
 * [onHeightMeasured] includes whatever padding [modifier] adds. A zero height is not reported: it
 * is the bar entering or leaving, not its size, and the pages are padded by the last real one.
 */
@Composable
internal fun ViewerBar(
    visible: Boolean,
    edge: ViewerBarEdge,
    modifier: Modifier = Modifier,
    onHeightMeasured: (heightPx: Int) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val direction = if (edge == ViewerBarEdge.Top) -1 else 1
    val offscreen: (Int) -> Int = { height -> height * direction }
    AnimatedVisibility(
        visible = visible,
        modifier =
            Modifier.onSizeChanged { if (it.height > 0) onHeightMeasured(it.height) }
                .then(modifier),
        enter =
            fadeIn(motion.slowEffectsSpec()) +
                slideInVertically(motion.fastSpatialSpec(), initialOffsetY = offscreen),
        exit =
            fadeOut(motion.slowEffectsSpec()) +
                slideOutVertically(motion.fastSpatialSpec(), targetOffsetY = offscreen),
    ) {
        content()
    }
}

internal enum class ViewerBarEdge {
    Top,
    Bottom,
}
