package com.composepdf.state

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.core.Animatable
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
import com.composepdf.remote.RemotePdfState

/**
 * A hoistable state object that manages the UI state and navigation for a PDF viewer.
 *
 * Tracks document navigation (current page, zoom level, and pan offsets) and manages
 * a persistent [LruCache] for high-resolution tiles.
 *
 * ## Programmatic control
 *
 * All imperative actions (scroll to page, zoom in/out, change fit mode) are exposed
 * directly on this object — mirroring the Compose convention used by `LazyListState`,
 * `ScrollState`, etc.
 *
 * ```kotlin
 * val pdfState = rememberPdfViewerState()
 * // In a coroutine:
 * pdfState.animateScrollToPage(4)
 * pdfState.zoomIn()
 * pdfState.setFitMode(FitMode.WIDTH)
 * ```
 *
 * @param initialPage The index of the page to be displayed initially. Defaults to 0.
 * @param initialZoom The initial magnification level. Defaults to 1.0f (fit-to-width).
 */
@Stable
class PdfViewerState(
    initialPage: Int = 0,
    initialZoom: Float = 1f
) {
    /** The index of the current page most visible in the viewport. */
    var currentPage: Int by mutableIntStateOf(initialPage)
        internal set

    /** Total number of pages in the current document. */
    var pageCount: Int by mutableIntStateOf(0)
        internal set

    /** Current magnification level. 1.0f means fit-to-width. */
    var zoom: Float by mutableFloatStateOf(initialZoom)
        internal set

    /** Horizontal translation offset in screen pixels. */
    var panX: Float by mutableFloatStateOf(0f)
        internal set

    /** Vertical translation offset in screen pixels. */
    var panY: Float by mutableFloatStateOf(0f)
        internal set

    /** Indicates if the document or pages are currently being loaded/rendered. */
    var isLoading: Boolean by mutableStateOf(true)
        internal set

    /** Stores any error encountered during the PDF lifecycle. */
    var error: Throwable? by mutableStateOf(null)
        internal set

    /** True if a user gesture (pinch, pan) is currently active. */
    var isGestureActive: Boolean by mutableStateOf(false)
        internal set

    /** State of the remote document loading if applicable. */
    var remoteState: RemotePdfState by mutableStateOf(RemotePdfState.Idle)
        internal set

    /**
     * Internal cache for high-resolution tiles.
     * Persists through gestures to provide "Double Buffering" visual stability.
     */
    private val tileCache = LruCache<String, Bitmap>(400)

    /**
     * Revision counter to notify Compose when the tile cache is updated.
     */
    var tileRevision by mutableIntStateOf(0)
        private set

    /** Retrieves a tile from the cache. */
    fun getTile(key: String): Bitmap? = tileCache.get(key)

    /** Stores a rendered tile and triggers UI update. */
    fun putTile(key: String, bitmap: Bitmap) {
        tileCache.put(key, bitmap)
        tileRevision++
    }

    /** Returns a snapshot of all currently cached tiles. */
    fun getAllTiles(): Map<String, Bitmap> = tileCache.snapshot()

    /**
     * Removes tiles that match the given predicate.
     * Used for selective invalidation (e.g., zoom level pruning).
     */
    fun pruneTiles(predicate: (String) -> Boolean) {
        val snapshot = tileCache.snapshot()
        var changed = false
        snapshot.keys.forEach { key ->
            if (predicate(key)) {
                tileCache.remove(key)
                changed = true
            }
        }
        if (changed) tileRevision++
    }

    /** Clears all high-resolution tiles. */
    fun clearTiles() {
        tileCache.evictAll()
        tileRevision++
    }

    /** True if a document is loaded and ready for interaction. */
    val isLoaded: Boolean get() = !isLoading && error == null && pageCount > 0

    /** Current pan offset as a Compose [Offset]. */
    @Suppress("unused")
    val offset: Offset get() = Offset(panX, panY)

    // -------------------------------------------------------------------------
    // Internal controller bridge
    // -------------------------------------------------------------------------

    /**
     * Reference to the active [PdfViewerController]. Set by the PdfViewer composable when the
     * controller is created and cleared when it is disposed.
     * All public API functions delegate to this controller.
     */
    internal var controller: PdfViewerController? = null

    /** The configured minimum zoom level. Returns 1f if no document is loaded yet. */
    val minZoom: Float get() = controller?.config?.minZoom ?: 1f

    /** The configured maximum zoom level. Returns 5f if no document is loaded yet. */
    val maxZoom: Float get() = controller?.config?.maxZoom ?: 5f

    // -------------------------------------------------------------------------
    // Public programmatic API
    // -------------------------------------------------------------------------

    /**
     * Instantly jumps to [pageIndex] without animation.
     *
     * @param pageIndex Zero-based page index to scroll to.
     */
    @Suppress("unused")
    fun scrollToPage(pageIndex: Int) {
        val ctrl = controller ?: return
        val target = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val pageTop = ctrl.pageTopDocY(target)
        panY = -(pageTop * zoom)
        currentPage = target
        ctrl.clampPanPublic()
        ctrl.requestRenderForVisiblePages()
    }

    /**
     * Smoothly animates the scroll to [pageIndex].
     *
     * Must be called from a coroutine (e.g. inside a `LaunchedEffect` or a click handler
     * wrapped with a `CoroutineScope`).
     *
     * @param pageIndex Zero-based page index to scroll to.
     * @param animationSpec Animation used for the scroll. Defaults to a spring.
     */
    @Suppress("unused")
    suspend fun animateScrollToPage(
        pageIndex: Int,
        animationSpec: AnimationSpec<Float> = spring()
    ) {
        val ctrl = controller ?: return
        val target = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val pageTop = ctrl.pageTopDocY(target)
        val targetPanY = -(pageTop * zoom)

        currentPage = target
        Animatable(panY).animateTo(
            targetValue = targetPanY,
            animationSpec = animationSpec
        ) {
            panY = value
            ctrl.clampPanPublic()
            ctrl.requestRenderForVisiblePages()
        }
        currentPage = target
    }

    /**
     * Instantly sets the zoom level centered on the viewport center.
     *
     * @param zoomLevel The absolute zoom level to apply.
     */
    fun setZoom(zoomLevel: Float) {
        val ctrl = controller ?: return
        val pivot = Offset(ctrl.viewportWidth / 2f, ctrl.viewportHeight / 2f)
        ctrl.onAnimatedZoomFrame(zoomLevel, pivot)
    }

    /**
     * Smoothly animates to the given absolute zoom level, centered on the viewport.
     *
     * @param zoomLevel The target zoom level.
     * @param animationSpec Animation used for the zoom transition.
     */
    @Suppress("unused")
    suspend fun animateZoomTo(
        zoomLevel: Float,
        animationSpec: AnimationSpec<Float> = spring()
    ) {
        val ctrl = controller ?: return
        val pivot = Offset(ctrl.viewportWidth / 2f, ctrl.viewportHeight / 2f)
        val clampedTarget = zoomLevel.coerceIn(ctrl.config.minZoom, ctrl.config.maxZoom)

        Animatable(zoom).animateTo(
            targetValue = clampedTarget,
            animationSpec = animationSpec
        ) {
            ctrl.onAnimatedZoomFrame(value, pivot)
        }
    }

    /**
     * Zooms in by [factor] relative to the current zoom, centered on the viewport.
     *
     * @param factor Multiplicative factor (e.g. 0.25f increases zoom by 25%).
     */
    fun zoomIn(factor: Float = 0.25f) {
        setZoom(zoom * (1f + factor))
    }

    /**
     * Zooms out by [factor] relative to the current zoom, centered on the viewport.
     *
     * @param factor Multiplicative factor (e.g. 0.25f decreases zoom by 25%).
     */
    fun zoomOut(factor: Float = 0.25f) {
        setZoom(zoom * (1f - factor))
    }

    /**
     * Resets the zoom to the fit-to-viewport level (the configured minimum zoom) with animation.
     */
    @Suppress("unused")
    suspend fun animateResetZoom(animationSpec: AnimationSpec<Float> = spring()) {
        val ctrl = controller ?: return
        animateZoomTo(ctrl.config.minZoom, animationSpec)
    }

    /**
     * Changes the [FitMode] of the viewer at runtime, rebuilding the page layout.
     *
     * Applied immediately without reloading the document.
     *
     * @param fitMode The new [FitMode] to apply.
     */
    @Suppress("unused")
    fun setFitMode(fitMode: FitMode) {
        controller?.updateConfig(controller!!.config.copy(fitMode = fitMode))
    }

    /**
     * Changes the [ScrollDirection] of the viewer at runtime.
     *
     * Note: changing scroll direction resets pan offsets to avoid an inconsistent viewport.
     *
     * @param direction The new [ScrollDirection] to apply.
     */
    @Suppress("unused")
    fun setScrollDirection(direction: ScrollDirection) {
        controller?.updateConfig(controller!!.config.copy(scrollDirection = direction))
    }

    /**
     * Enables or disables night mode (color inversion) at runtime.
     */
    @Suppress("unused")
    fun setNightMode(enabled: Boolean) {
        controller?.updateConfig(controller!!.config.copy(isNightModeEnabled = enabled))
    }

    /**
     * Enables or disables page snapping at runtime.
     */
    @Suppress("unused")
    fun setPageSnapping(enabled: Boolean) {
        controller?.updateConfig(controller!!.config.copy(isPageSnappingEnabled = enabled))
    }

    // -------------------------------------------------------------------------
    // Internal lifecycle
    // -------------------------------------------------------------------------

    /** Resets the state to initial values. */
    internal fun reset() {
        currentPage = 0; pageCount = 0; zoom = 1f; panX = 0f; panY = 0f
        isLoading = true; error = null; isGestureActive = false
        clearTiles()
    }

    companion object {
        /** Saver for persisting state across configuration changes. */
        val Saver: Saver<PdfViewerState, *> = listSaver(
            save = { listOf(it.currentPage, it.zoom, it.panX, it.panY) },
            restore = {
                PdfViewerState(initialPage = it[0] as Int, initialZoom = it[1] as Float).also { s ->
                    s.panX = it[2] as Float; s.panY = it[3] as Float
                }
            }
        )
    }
}
