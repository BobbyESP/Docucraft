/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bobbyesp.docucraft.R
import com.composepdf.PdfViewerState
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

/**
 * A fast scroller along the trailing edge, for documents long enough to need one. It shows while
 * the document moves and fades after a pause. Dragging its thumb jumps through the document, with
 * the page number beside it. TalkBack sees it as an adjustable control, so it works without
 * dragging too.
 *
 * Built only on [PdfViewerState]'s public API. It replaces the engine's passive scroll indicator,
 * which the viewer turns off.
 *
 * @param contentTop Where the viewer's content area starts, from the viewer's top: its top content
 *   padding. [modifier] should place this over exactly that area.
 */
@Composable
fun PdfFastScroller(state: PdfViewerState, contentTop: Dp, modifier: Modifier = Modifier) {
    val pageCount = state.pageCount
    if (pageCount < MinPages) return

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var recentlyScrolled by remember { mutableStateOf(false) }

    // Visible while the document moves, and for a moment after.
    LaunchedEffect(state) {
        snapshotFlow { state.panY to state.zoom }
            .drop(1)
            .collectLatest {
                recentlyScrolled = true
                delay(HideDelay)
                recentlyScrolled = false
            }
    }

    val visible = dragging || recentlyScrolled
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "fastScrollerAlpha")
    val pageLabel = stringResource(R.string.page_indicator, state.currentPage + 1, pageCount)
    val description = stringResource(R.string.fast_scroller)

    BoxWithConstraints(
        modifier =
            modifier.fillMaxHeight().width(TouchWidth).alpha(alpha).semantics {
                contentDescription = description
                stateDescription = pageLabel
                progressBarRangeInfo =
                    ProgressBarRangeInfo(
                        current = (state.currentPage + 1).toFloat(),
                        range = 1f..pageCount.toFloat(),
                        steps = (pageCount - 2).coerceAtLeast(0),
                    )
                setProgress { target ->
                    state.scrollToPage(target.roundToInt() - 1)
                    true
                }
            }
    ) {
        val density = LocalDensity.current
        val trackPx = with(density) { (maxHeight - ThumbHeight).toPx() }
        val fraction =
            if (dragging) dragFraction
            else
                state.scrollFraction(
                    contentTopPx = with(density) { contentTop.toPx() },
                    contentHeightPx = with(density) { maxHeight.toPx() },
                )
        val thumbOffset = (fraction * trackPx).roundToInt()

        Box(
            modifier =
                Modifier.fillMaxHeight().width(TouchWidth).pointerInput(state, trackPx) {
                    val thumbHalf = ThumbHeight.toPx() / 2f
                    detectVerticalDragGestures(
                        onDragStart = { start ->
                            dragging = true
                            dragFraction = ((start.y - thumbHalf) / trackPx).coerceIn(0f, 1f)
                            state.scrollToPage(pageAt(dragFraction, pageCount))
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        change.consume()
                        dragFraction = ((change.position.y - thumbHalf) / trackPx).coerceIn(0f, 1f)
                        state.scrollToPage(pageAt(dragFraction, pageCount))
                    }
                }
        ) {
            Box(
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .offset { IntOffset(0, thumbOffset) }
                        .padding(end = 4.dp)
                        .size(width = if (dragging) 8.dp else 6.dp, height = ThumbHeight)
                        .background(
                            color =
                                if (dragging) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = CircleShape,
                        )
            )
        }

        // The page, beside the thumb, while dragging.
        AnimatedVisibility(
            visible = dragging,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                // The track is narrower than the label: measure it free of that width and let it
                // overflow to the left, beside the track.
                Modifier.align(Alignment.TopEnd)
                    .offset { IntOffset(-TouchWidth.roundToPx(), thumbOffset) }
                    .wrapContentWidth(align = Alignment.End, unbounded = true),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    text = pageLabel,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * The page number, shown for a moment at the top when the page changes while the bars are hidden:
 * hiding the bars must not hide where the reader is.
 */
@Composable
fun PageIndicatorPill(
    currentPage: Int,
    pageCount: Int,
    barsVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    var showing by remember { mutableStateOf(false) }

    LaunchedEffect(currentPage, barsVisible) {
        if (barsVisible || pageCount == 0) {
            showing = false
            return@LaunchedEffect
        }
        showing = true
        delay(HideDelay)
        showing = false
    }

    AnimatedVisibility(visible = showing, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ) {
            Text(
                text = stringResource(R.string.page_indicator, currentPage + 1, pageCount),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * How far through the document the content area is, from the pages' on-screen bounds: `0` with the
 * first page's top at the top of the content area, `1` with the last page's bottom at its bottom.
 */
private fun PdfViewerState.scrollFraction(contentTopPx: Float, contentHeightPx: Float): Float {
    val first = pageRectInViewer(0) ?: return 0f
    val last = pageRectInViewer(pageCount - 1) ?: return 0f
    val scrollable = (last.bottom - first.top) - contentHeightPx
    if (scrollable <= 0f) return 0f
    return ((contentTopPx - first.top) / scrollable).coerceIn(0f, 1f)
}

private fun pageAt(fraction: Float, pageCount: Int): Int =
    (fraction * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)

/** Below this a document is short enough to scroll by hand. */
private const val MinPages = 3

private val HideDelay = 1_200.milliseconds
private val ThumbHeight = 48.dp
private val TouchWidth = 32.dp
