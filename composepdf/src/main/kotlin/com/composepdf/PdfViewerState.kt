package com.composepdf

import android.graphics.Bitmap
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
import com.composepdf.internal.service.cache.bitmap.BitmapPool
import com.composepdf.internal.logic.PdfViewerStateControllerBridge
import com.composepdf.internal.logic.PublishedTile
import com.composepdf.internal.logic.ViewerSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.math.abs

/**
 * A hoistable state object that manages the UI state and navigation for a PDF viewer.
 *
 * This version enforces strict memory management by requiring a [BitmapPool] and a [CoroutineScope]
 * to handle asynchronous tile eviction and pool returns.
 */
@Stable
class PdfViewerState(
    initialPage: Int = 0,
    initialZoom: Float = 1f,
    internal val bitmapPool: BitmapPool = BitmapPool(),
    internal val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
) {
    private val session = ViewerSessionState(scope, bitmapPool)

    /** The index of the current page most visible in the viewport. */
    var currentPage: Int by mutableIntStateOf(initialPage)
        internal set

    /** Total number of pages in the current document. */
    var pageCount: Int
        get() = session.pageCount
        internal set(value) {
            session.pageCount = value
        }

    /** Current magnification level. 1.0f means fit-to-width. */
    var zoom: Float by mutableFloatStateOf(initialZoom)
        internal set

    /**
     * The stepped zoom level currently being rendered and displayed for high-res tiles.
     */
    var activeSteppedZoom: Float by mutableFloatStateOf(1f)
        internal set

    /** Horizontal translation offset in screen pixels. */
    var panX: Float by mutableFloatStateOf(0f)
        internal set

    /** Vertical translation offset in screen pixels. */
    var panY: Float by mutableFloatStateOf(0f)
        internal set

    /** Current scroll velocity in pixels per second. */
    var scrollVelocity: Offset by mutableStateOf(Offset.Zero)
        internal set

    /** Indicates if the document or pages are currently being loaded/rendered. */
    var isLoading: Boolean
        get() = session.isLoading
        internal set(value) {
            session.isLoading = value
        }

    /** Stores any error encountered during the PDF lifecycle. */
    var error: Throwable?
        get() = session.error
        internal set(value) {
            session.error = value
        }

    /** True if a user gesture (pinch, pan) is currently active. */
    var isGestureActive: Boolean by mutableStateOf(false)
        internal set

    /** State of the remote document loading if applicable. */
    var remoteState: RemotePdfState
        get() = session.remoteState
        internal set(value) {
            session.remoteState = value
        }

    /** Revision counter to notify Compose when the tile cache is updated. */
    val tileRevision: Int get() = session.tileRevision

    internal fun getTile(key: String): Bitmap? = session.getTile(key)

    internal fun getAllTiles(): Map<String, Bitmap> = session.getAllTiles()

    internal suspend fun putTile(key: String, bitmap: Bitmap) = session.putTile(key, bitmap)

    internal fun getImageBitmapTilesForPage(pageIndex: Int): List<PublishedTile> =
        session.getImageBitmapTilesForPage(pageIndex)

    internal suspend fun pruneTiles(predicate: (String) -> Boolean) = session.pruneTiles(predicate)

    internal suspend fun clearTiles() = session.clearTiles()

    internal fun beginDocumentLoad() {
        session.beginDocumentLoad()
    }

    internal fun updateRemoteDocumentState(state: RemotePdfState) {
        session.updateRemoteState(state)
    }

    internal fun completeDocumentLoad(pageCount: Int) {
        session.completeDocumentLoad(pageCount)
    }

    internal fun failDocumentLoad(error: Throwable) {
        session.failDocumentLoad(error)
    }

    /** True if a document is loaded and ready for interaction. */
    val isLoaded: Boolean get() = session.isLoaded

    internal var controller: PdfViewerStateControllerBridge? = null

    val minZoom: Float get() = controller?.viewerConfig?.minZoom ?: 1f
    val maxZoom: Float get() = controller?.viewerConfig?.maxZoom ?: 5f

    // -------------------------------------------------------------------------
    // Public Programmatic API
    // -------------------------------------------------------------------------

    /** Instantly jumps to [pageIndex] without animation. */
    fun scrollToPage(pageIndex: Int) {
        val ctrl = controller ?: return
        val target = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val (targetPanX, targetPanY) = ctrl.computeCenteredPanForPage(target)
        
        panX = targetPanX
        panY = targetPanY
        currentPage = target
        ctrl.clampPan()
        ctrl.requestRenderForVisiblePages()
    }

    /** Smoothly animates the scroll to [pageIndex]. */
    suspend fun animateScrollToPage(
        pageIndex: Int,
        animationSpec: AnimationSpec<Float> = spring()
    ) {
        val ctrl = controller ?: return
        val target = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val (targetPanX, targetPanY) = ctrl.computeCenteredPanForPage(target)

        val startPanX = panX
        val startPanY = panY

        currentPage = target
        Animatable(0f).animateTo(
            targetValue = 1f,
            animationSpec = animationSpec
        ) {
            panX = startPanX + (targetPanX - startPanX) * value
            panY = startPanY + (targetPanY - startPanY) * value
            ctrl.clampPan()
            ctrl.requestRenderForVisiblePages()
        }
        currentPage = target
    }

    /** Instantly sets the zoom level centered on the viewport center. */
    fun setZoom(zoomLevel: Float) {
        val ctrl = controller ?: return
        val pivot = Offset(ctrl.viewportWidth / 2f, ctrl.viewportHeight / 2f)
        ctrl.onAnimatedZoomFrame(zoomLevel, pivot)
    }

    /** Smoothly animates to the given absolute zoom level, centered on the viewport. */
    suspend fun animateZoomTo(
        zoomLevel: Float,
        animationSpec: AnimationSpec<Float> = spring()
    ) {
        val ctrl = controller ?: return
        val pivot = Offset(ctrl.viewportWidth / 2f, ctrl.viewportHeight / 2f)
        val clampedTarget = zoomLevel.coerceIn(ctrl.viewerConfig.minZoom, ctrl.viewerConfig.maxZoom)

        Animatable(zoom).animateTo(
            targetValue = clampedTarget,
            animationSpec = animationSpec
        ) {
            ctrl.onAnimatedZoomFrame(value, pivot)
        }
    }

    /** Zooms in by [factor] relative to the current zoom, centered on the viewport. */
    fun zoomIn(factor: Float = 0.25f) {
        setZoom(zoom * (1f + factor))
    }

    /** Zooms out by [factor] relative to the current zoom, centered on the viewport. */
    fun zoomOut(factor: Float = 0.25f) {
        setZoom(zoom * (1f - factor))
    }

    /** Resets the zoom to fit the current page in the viewport. */
    suspend fun animateResetZoom(animationSpec: AnimationSpec<Float> = spring()) {
        val ctrl = controller ?: return
        val targetZoom = ctrl.computeFitPageZoom(currentPage)
        val alreadyFit = abs(zoom - targetZoom) / targetZoom < 0.02f

        if (alreadyFit) {
            val (targetPanX, targetPanY) = ctrl.computeCenteredPanForPage(currentPage)
            val needsX = abs(panX - targetPanX) > 1f
            val needsY = abs(panY - targetPanY) > 1f
            if (!needsX && !needsY) return

            val startPanX = panX
            val startPanY = panY

            Animatable(0f).animateTo(1f, animationSpec) {
                if (needsX) panX = startPanX + (targetPanX - startPanX) * value
                if (needsY) panY = startPanY + (targetPanY - startPanY) * value
                ctrl.clampPan()
                ctrl.requestRenderForVisiblePages()
            }
        } else {
            animateZoomTo(targetZoom, animationSpec)
        }
    }

    /** Changes the [FitMode] of the viewer at runtime. */
    fun setFitMode(fitMode: FitMode) {
        val ctrl = controller ?: return
        ctrl.updateConfig(ctrl.viewerConfig.copy(fitMode = fitMode))
    }

    /** Changes the [ScrollDirection] of the viewer at runtime. */
    fun setScrollDirection(direction: ScrollDirection) {
        val ctrl = controller ?: return
        ctrl.updateConfig(ctrl.viewerConfig.copy(scrollDirection = direction))
    }

    /** Enables or disables night mode (color inversion) at runtime. */
    fun setNightMode(enabled: Boolean) {
        val ctrl = controller ?: return
        ctrl.updateConfig(ctrl.viewerConfig.copy(isNightModeEnabled = enabled))
    }

    /** Enables or disables page snapping at runtime. */
    fun setPageSnapping(enabled: Boolean) {
        val ctrl = controller ?: return
        ctrl.updateConfig(ctrl.viewerConfig.copy(isPageSnappingEnabled = enabled))
    }

    internal suspend fun reset() {
        currentPage = 0
        zoom = 1f
        activeSteppedZoom = 1f
        panX = 0f
        panY = 0f
        scrollVelocity = Offset.Zero
        isGestureActive = false
        session.beginDocumentLoad()
        session.clearTiles()
    }

    companion object {
        /**
         * Creates a [Saver] for [PdfViewerState].
         * Note: The [scope] is not saved; a new one must be provided upon restoration.
         */
        fun saver(bitmapPool: BitmapPool, scope: CoroutineScope): Saver<PdfViewerState, *> = listSaver(
            save = { listOf(it.currentPage, it.zoom, it.panX, it.panY) },
            restore = {
                PdfViewerState(
                    initialPage = it[0] as Int,
                    initialZoom = it[1] as Float,
                    bitmapPool = bitmapPool,
                    scope = scope
                ).also { s ->
                    s.panX = it[2] as Float; s.panY = it[3] as Float
                }
            }
        )
    }
}
