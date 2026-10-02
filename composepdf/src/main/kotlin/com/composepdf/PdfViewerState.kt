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
import com.composepdf.internal.logic.PageAnchor
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
 *
 * @param initialPosition Where to open the document, to the point on the page. When given, it is
 *   used instead of [initialPage]: a host that kept a [readingPosition] hands it back here.
 */
@Stable
class PdfViewerState(
    initialPage: Int = 0,
    initialZoom: Float = 1f,
    internal val bitmapPool: BitmapPool = BitmapPool(),
    initialPosition: PdfReadingPosition? = null,
) {
    /** The index of the page most visible in the viewport. */
    var currentPage: Int by
        mutableIntStateOf(initialPosition?.pageIndex?.coerceAtLeast(0) ?: initialPage)
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

    /**
     * A position to take up once the document is laid out: the one restored by [saver], or the one
     * asked for through the constructor. Loading a document starts from zero, so without this both
     * were overwritten before they could be shown. Consumed by the first load that can apply it; a
     * later load (a different document) starts from the top as before.
     */
    internal var pendingPosition: PendingPosition? =
        when {
            initialPosition != null ->
                PendingPosition(
                    PageAnchor(
                        pageIndex = initialPosition.pageIndex.coerceAtLeast(0),
                        // Whatever a host kept: a fraction that is not a number would become a pan
                        // that is not one either.
                        fraction =
                            initialPosition.fraction.takeUnless { it.isNaN() }?.coerceIn(0f, 1f)
                                ?: 0f,
                    ),
                    initialZoom,
                )
            initialPage != 0 || initialZoom != 1f ->
                PendingPosition(PageAnchor(initialPage, 0.5f), initialZoom)
            else -> null
        }

    /**
     * Where the reader is: the point of the document at the centre of the content area, as a page
     * and a fraction along it. It does not depend on the viewport, so a host can keep it and start
     * a viewer there later, on a screen of another shape (`initialPosition`).
     *
     * Reading it observes pan and zoom: it changes on every frame of a scroll, so a host that
     * stores it waits for it to settle. Until a document is laid out it is the position the state
     * was created or restored with, which is not yet where anything is on screen: [isLoaded] tells
     * the two apart.
     */
    val readingPosition: PdfReadingPosition
        get() = positionToSave().anchor.let { PdfReadingPosition(it.pageIndex, it.fraction) }

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

    /**
     * Resolves [position], in viewer pixels, against the document as a tap there would be: the page
     * under it and where on that page. For a finger that stays still while the document moves under
     * it, such as a selection handle held near an edge while the document scrolls.
     */
    fun hitTest(position: Offset): PdfTapEvent =
        controller?.tapEventAt(position)
            ?: PdfTapEvent(position, pageIndex = null, pagePosition = null)

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

    /**
     * Moves the document by [delta] pixels at once, as a finger dragging it would: a positive `y`
     * moves it down, bringing earlier content into view. Stays within the document's bounds, and
     * returns how far it actually moved. For scrolling while the caller owns the gesture, such as a
     * selection dragged to an edge.
     */
    fun panBy(delta: Offset): Offset {
        val ctrl = controller ?: return Offset.Zero
        ctrl.stopAnimations()
        return ctrl.panBy(delta)
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

    /**
     * Smoothly scrolls to [position] on [pageIndex], normalized to the page, bringing it to the
     * start of the content area: where a reader expects the target of a link to appear. The zoom
     * stays as it is. Without a position, the page's start is brought there instead, which unlike
     * [animateScrollToPage] shows its first lines even when the page is taller than the screen.
     */
    suspend fun animateScrollTo(
        pageIndex: Int,
        position: Offset? = null,
        animationSpec: AnimationSpec<Float> = spring(),
    ) {
        val ctrl = controller ?: return
        val target = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val pan = ctrl.panForPagePoint(target, position)
        ctrl.animatePanTo(pan.x, pan.y, animationSpec)
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

    /**
     * What [saver] keeps: a position not yet taken up is kept as it is, since nothing has moved;
     * otherwise the one on screen, as an anchor, because raw pan values are meaningless for the
     * viewport a rotation brings.
     */
    internal fun positionToSave(): PendingPosition {
        pendingPosition?.let {
            return it
        }
        val anchor =
            controller?.layout()?.anchorAtContentCenter(panX, panY, zoom)
                ?: PageAnchor(currentPage, 0.5f)
        return PendingPosition(anchor, zoom)
    }

    companion object {
        /**
         * Creates a [Saver] restoring the reading position and zoom across recreation. The position
         * is stored as a point on a page, not as pan, so it lands on the same line even when the
         * viewport comes back a different shape.
         */
        fun saver(bitmapPool: BitmapPool): Saver<PdfViewerState, *> =
            listSaver(
                save = {
                    val position = it.positionToSave()
                    listOf(position.anchor.pageIndex, position.anchor.fraction, position.zoom)
                },
                restore = {
                    PdfViewerState(bitmapPool = bitmapPool).also { state ->
                        val position =
                            PendingPosition(
                                anchor = PageAnchor(it[0] as Int, it[1] as Float),
                                zoom = it[2] as Float,
                            )
                        state.pendingPosition = position
                        state.currentPage = position.anchor.pageIndex
                        state.zoom = position.zoom
                    }
                },
            )
    }
}

/** A reading position waiting for a layout to be applied to. */
internal data class PendingPosition(val anchor: PageAnchor, val zoom: Float)
