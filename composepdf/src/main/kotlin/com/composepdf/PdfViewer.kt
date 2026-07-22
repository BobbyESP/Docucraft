/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composepdf.internal.engine.BitmapPool
import com.composepdf.internal.logic.PdfViewerController
import com.composepdf.internal.logic.ResolvedViewerConfig
import com.composepdf.internal.ui.PdfDocumentCanvas
import com.composepdf.internal.ui.PdfPageLoadingOverlay
import com.composepdf.internal.ui.gesture.pdfViewerGestures
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

/**
 * A high-performance PDF viewer for Jetpack Compose.
 *
 * Rendering runs in a tile-based engine that keeps interaction fluid regardless of document size;
 * the composable itself only displays engine state. Gestures cover panning, inertial fling,
 * pinch-to-zoom with a stable focal point and rubber-band limits, double-tap zoom and quick scale
 * (double tap + drag), all interruptible mid-flight.
 *
 * The viewer is configured through small immutable specs with sensible defaults; state and
 * navigation are exposed through the hoisted [PdfViewerState].
 *
 * @param source The document to display.
 * @param modifier Modifier applied to the viewer container.
 * @param state Hoisted state object for observing and driving the viewer.
 * @param layout Page arrangement: direction, fit, spacing, snapping.
 * @param zoomSpec Zoom limits and double-tap behavior.
 * @param gestureSpec Which gestures are enabled.
 * @param renderSpec Rendering quality and prefetch tuning.
 * @param style Colors, page decorations and indicators. Use [PdfViewerDefaults.style] to inherit
 *   the Material theme.
 * @param onTap Called for single taps that are not part of a gesture, with page hit-test info.
 * @param onLongPress Called for long presses, with page hit-test info.
 * @param onPageChange Called when the page most visible in the viewport changes.
 * @param onDocumentLoad Called with the page count once the document is ready.
 * @param onError Called when the document fails to load.
 * @param overlay Content drawn above the pages, in viewer coordinates. Combine with
 *   [PdfViewerState.pageRectInViewer] and [PdfViewerState.visiblePages] to anchor highlights,
 *   scrubbers or annotations to pages.
 * @param loadingContent Shown while the document loads.
 * @param errorContent Shown when loading fails.
 */
@Composable
fun PdfViewer(
    source: PdfSource,
    modifier: Modifier = Modifier,
    state: PdfViewerState = rememberPdfViewerState(),
    layout: PdfLayoutSpec = PdfLayoutSpec(),
    zoomSpec: PdfZoomSpec = PdfZoomSpec(),
    gestureSpec: PdfGestureSpec = PdfGestureSpec(),
    renderSpec: PdfRenderSpec = PdfRenderSpec(),
    style: PdfViewerStyle = PdfViewerDefaults.style(),
    onTap: ((PdfTapEvent) -> Unit)? = null,
    onLongPress: ((PdfTapEvent) -> Unit)? = null,
    onPageChange: ((Int) -> Unit)? = null,
    onDocumentLoad: ((pageCount: Int) -> Unit)? = null,
    onError: ((Throwable) -> Unit)? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    loadingContent: @Composable BoxScope.() -> Unit = { PdfViewerDefaults.LoadingContent() },
    errorContent: @Composable BoxScope.(Throwable) -> Unit = {
        PdfViewerDefaults.ErrorContent(it)
    },
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val resolvedConfig =
        remember(layout, zoomSpec, renderSpec, density) {
            ResolvedViewerConfig.from(
                layout = layout,
                zoom = zoomSpec,
                render = renderSpec,
                pageSpacingPx = with(density) { layout.pageSpacing.toPx() },
            )
        }

    // Sourced from composition (via the Recomposer) rather than hand-rolled, so the scope
    // carries a MonotonicFrameClock — required by every Animatable/animateDecay call the
    // controller launches on it (fling, quick scale, double-tap zoom, snap/rubber-band settle).
    val coroutineScope = rememberCoroutineScope()
    val controller =
        remember(context, state) {
            PdfViewerController(context, state, resolvedConfig, coroutineScope)
        }

    LaunchedEffect(controller, resolvedConfig) { controller.updateConfig(resolvedConfig) }

    DisposableEffect(controller) {
        state.controller = controller
        onDispose {
            state.controller = null
            controller.close()
        }
    }

    LaunchedEffect(source, controller) { controller.loadDocument(source) }

    val latestOnPageChange by rememberUpdatedState(onPageChange)
    val latestOnError by rememberUpdatedState(onError)
    val latestOnDocumentLoad by rememberUpdatedState(onDocumentLoad)
    LaunchedEffect(state.currentPage) { latestOnPageChange?.invoke(state.currentPage) }
    LaunchedEffect(state.error) { state.error?.let { latestOnError?.invoke(it) } }
    LaunchedEffect(state.pageCount) {
        if (state.pageCount > 0) latestOnDocumentLoad?.invoke(state.pageCount)
    }

    // Collected as State and read inside draw/layout blocks only — publishing a bitmap never
    // recomposes the viewer.
    val pages = controller.renderedPages.collectAsStateWithLifecycle()
    val tiles = controller.tiles.collectAsStateWithLifecycle()

    val overscrollEffect = rememberOverscrollEffect()

    val indicatorAlpha = remember { Animatable(0f) }
    LaunchedEffect(state, style.scrollIndicator != null) {
        if (style.scrollIndicator == null) return@LaunchedEffect
        snapshotFlow { Triple(state.panX, state.panY, state.zoom) }
            .drop(1)
            .collectLatest {
                indicatorAlpha.animateTo(1f, tween(durationMillis = 100))
                delay(SCROLL_INDICATOR_HIDE_DELAY_MS.milliseconds)
                indicatorAlpha.animateTo(0f, tween(durationMillis = 450))
            }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clipToBounds()
                .then(
                    if (style.containerColor.isSpecified) {
                        Modifier.background(style.containerColor)
                    } else {
                        Modifier
                    }
                )
                .onSizeChanged { size ->
                    controller.onViewportSizeChanged(size.width.toFloat(), size.height.toFloat())
                }
                .pdfViewerGestures(
                    controller = controller,
                    state = state,
                    gestureSpec = gestureSpec,
                    zoomSpec = zoomSpec,
                    overscrollEffect = overscrollEffect,
                    onTap = onTap,
                    onLongPress = onLongPress,
                    enabled = state.isLoaded,
                ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.error != null -> errorContent(state.error!!)

            state.isLoading -> loadingContent()

            state.isLoaded -> {
                Box(Modifier.matchParentSize().overscroll(overscrollEffect)) {
                    PdfDocumentCanvas(
                        controller = controller,
                        state = state,
                        pages = pages,
                        tiles = tiles,
                        style = style,
                        scrollIndicatorAlpha = { indicatorAlpha.value },
                        modifier = Modifier.matchParentSize(),
                    )
                    if (style.showPageLoadingIndicator) {
                        PdfPageLoadingOverlay(
                            controller = controller,
                            state = state,
                            pages = pages,
                            tiles = tiles,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
                overlay?.invoke(this)
            }
        }
    }
}

/** Creates and remembers a [PdfViewerState] that survives configuration changes. */
@Composable
fun rememberPdfViewerState(initialPage: Int = 0, initialZoom: Float = 1f): PdfViewerState {
    val bitmapPool = remember { BitmapPool() }
    return rememberSaveable(saver = PdfViewerState.saver(bitmapPool)) {
        PdfViewerState(initialPage, initialZoom, bitmapPool)
    }
}

private const val SCROLL_INDICATOR_HIDE_DELAY_MS = 800L
