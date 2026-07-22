/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.composepdf.internal.engine.BitmapPool
import com.composepdf.internal.logic.ViewerController
import kotlin.math.abs

/**
 * A hoistable state object exposing the viewer's interaction state and a programmatic navigation
 * API.
 *
 * Configuration (layout, zoom limits, style) is owned by the [PdfViewer] call site and flows down
 * through recomposition; this object only holds what changes through interaction. All animation
 * commands share one interruption domain with gestures — starting any of them cancels ongoing
 * motion, and a touch cancels them.
 */
@Stable
class PdfViewerState(
    initialPage: Int = 0,
    initialZoom: Float = 1f,
    internal val bitmapPool: BitmapPool = BitmapPool(),
) {
    /** The index of the page most visible in the viewport. */
    var currentPage: Int by mutableIntStateOf(initialPage)
        internal set

    /** Total number of pages in the current document. */
    var pageCount: Int by mutableIntStateOf(0)
        internal set

    /** Current magnification. `1f` shows pages at their fitted size. */
    var zoom: Float by mutableFloatStateOf(initialZoom)
        internal set

    /** Horizontal translation of the document in viewer pixels. */
    var panX: Float by mutableFloatStateOf(0f)
        internal set

    /** Vertical translation of the document in viewer pixels. */
    var panY: Float by mutableFloatStateOf(0f)
        internal set

    /** Current scroll velocity in pixels per second (during drags and flings). */
    var scrollVelocity: Offset by mutableStateOf(Offset.Zero)
        internal set

    /** True while the document is loading. */
    var isLoading: Boolean by mutableStateOf(true)
        internal set

    /** The error that stopped the document from loading, if any. */
    var error: Throwable? by mutableStateOf(null)
        internal set

    /** True while the user's fingers are interacting with the viewer. */
    var isGestureActive: Boolean by mutableStateOf(false)
        internal set

    /** State of remote document loading, when the source is remote. */
    var remoteState: RemotePdfState by mutableStateOf(RemotePdfState.Idle)
        internal set

    /** True when a document is loaded and ready for interaction. */
    val isLoaded: Boolean
        get() = !isLoading && error == null && pageCount > 0

    internal var controller: ViewerController? = null

    /** The effective minimum committed zoom. */
    val minZoom: Float
        get() = controller?.config?.minZoom ?: 1f

    /** The effective maximum committed zoom. */
    val maxZoom: Float
        get() = controller?.config?.maxZoom ?: 8f

    // ------------------------------------------------------------------ geometry queries

    /** Indices of the pages currently intersecting the viewport. */
    val visiblePages: IntRange
        get() = controller?.visiblePageIndices() ?: IntRange.EMPTY

    /**
     * The on-screen bounds of [pageIndex] in viewer coordinates, or `null` when unknown. Reading
     * this inside a composable keeps custom overlays (highlights, scrubbers, thumbnails) in sync
     * with panning and zooming.
     */
    fun pageRectInViewer(pageIndex: Int): Rect? {
        val ctrl = controller ?: return null
        val layout = ctrl.layout()
        if (layout.isEmpty || pageIndex < 0 || pageIndex >= pageCount) return null
        val left = layout.pageScreenLeft(pageIndex, panX, zoom)
        val top = layout.pageScreenTop(pageIndex, panY, zoom)
        return Rect(
            left = left,
            top = top,
            right = left + layout.pageWidthPx(pageIndex) * zoom,
            bottom = top + layout.pageHeightPx(pageIndex) * zoom,
        )
    }

    // ------------------------------------------------------------------ document lifecycle

    internal fun beginDocumentLoad() {
        pageCount = 0
        isLoading = true
        error = null
        remoteState = RemotePdfState.Idle
    }

    internal fun updateRemoteDocumentState(state: RemotePdfState) {
        remoteState = state
    }

    internal fun completeDocumentLoad(pageCount: Int) {
        this.pageCount = pageCount
        isLoading = false
        error = null
    }

    internal fun failDocumentLoad(error: Throwable) {
        this.error = error
        isLoading = false
    }

    internal fun reset() {
        currentPage = 0
        zoom = 1f
        panX = 0f
        panY = 0f
        scrollVelocity = Offset.Zero
        isGestureActive = false
        beginDocumentLoad()
    }

    // ------------------------------------------------------------------ commands

    /** Stops any running scroll, zoom or fling animation at its current value. */
    fun stopAnimations() {
        controller?.stopAnimations()
    }

    /** Instantly jumps to [pageIndex]. */
    fun scrollToPage(pageIndex: Int) {
        val ctrl = controller ?: return
        ctrl.stopAnimations()
        val target = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val pan = ctrl.centeredPanForPage(target)
        ctrl.panBy(Offset(pan.x - panX, pan.y - panY))
        currentPage = target
    }

    /** Smoothly scrolls to [pageIndex]. */
    suspend fun animateScrollToPage(
        pageIndex: Int,
        animationSpec: AnimationSpec<Float> = spring(),
    ) {
        val ctrl = controller ?: return
        val target = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val pan = ctrl.centeredPanForPage(target)
        currentPage = target
        ctrl.animatePanTo(pan.x, pan.y, animationSpec)
        currentPage = target
    }

    /** Instantly sets the zoom, centered on the viewport. */
    fun setZoom(zoomLevel: Float) {
        val ctrl = controller ?: return
        ctrl.stopAnimations()
        val target = zoomLevel.coerceIn(minZoom, maxZoom)
        ctrl.zoomTo(target, Offset(ctrl.viewportWidth / 2f, ctrl.viewportHeight / 2f))
    }

    /** Smoothly animates to an absolute zoom level, centered on the viewport. */
    suspend fun animateZoomTo(zoomLevel: Float, animationSpec: AnimationSpec<Float> = spring()) {
        controller?.animateZoomTo(zoomLevel, pivot = null, animationSpec = animationSpec)
    }

    /** Zooms in by [factor] relative to the current zoom. */
    fun zoomIn(factor: Float = 0.25f) {
        setZoom(zoom * (1f + factor))
    }

    /** Zooms out by [factor] relative to the current zoom. */
    fun zoomOut(factor: Float = 0.25f) {
        setZoom(zoom * (1f - factor))
    }

    /** Animates back to the current page's fit zoom, re-centering if already fitted. */
    suspend fun animateResetZoom(animationSpec: AnimationSpec<Float> = spring()) {
        val ctrl = controller ?: return
        val targetZoom = ctrl.fitPageZoom(currentPage)
        val alreadyFit = abs(zoom - targetZoom) / targetZoom < 0.02f

        if (alreadyFit) {
            val pan = ctrl.centeredPanForPage(currentPage)
            ctrl.animatePanTo(pan.x, pan.y, animationSpec)
        } else {
            ctrl.animateZoomTo(targetZoom, pivot = null, animationSpec = animationSpec)
        }
    }

    companion object {
        /** Creates a [Saver] restoring page, zoom and pan across process recreation. */
        fun saver(bitmapPool: BitmapPool): Saver<PdfViewerState, *> =
            listSaver(
                save = { listOf(it.currentPage, it.zoom, it.panX, it.panY) },
                restore = {
                    PdfViewerState(
                            initialPage = it[0] as Int,
                            initialZoom = it[1] as Float,
                            bitmapPool = bitmapPool,
                        )
                        .also { s ->
                            s.panX = it[2] as Float
                            s.panY = it[3] as Float
                        }
                },
            )
    }
}
