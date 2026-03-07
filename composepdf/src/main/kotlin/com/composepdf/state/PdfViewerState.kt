package com.composepdf.state

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
import com.composepdf.remote.RemotePdfState
import kotlin.math.abs

/**
 * A hoistable state object that manages the UI state and navigation for a PDF viewer.
 */
@Stable
class PdfViewerState(
    initialPage: Int = 0,
    initialZoom: Float = 1f
) {
    private val session = ViewerSessionState()

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
     * Filtering tiles by this value prevents "Tile Soup".
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

    /**
     * Revision counter to notify Compose when the tile cache is updated.
     */
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

    // -------------------------------------------------------------------------
    // Internal controller bridge
    // -------------------------------------------------------------------------

    internal var controller: PdfViewerStateControllerBridge? = null

    val minZoom: Float get() = controller?.viewerConfig?.minZoom ?: 1f
    val maxZoom: Float get() = controller?.viewerConfig?.maxZoom ?: 5f

    @Suppress("unused")
    val fitDocumentZoom: Float get() = controller?.computeFitDocumentZoom() ?: minZoom

    @Suppress("unused")
    val fitPageZoom: Float get() = controller?.computeFitPageZoom(currentPage) ?: minZoom

    // -------------------------------------------------------------------------
    // Public programmatic API
    // -------------------------------------------------------------------------

    @Suppress("unused")
    fun scrollToPage(pageIndex: Int) {
        val ctrl = controller ?: return
        val target = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val pageTop = ctrl.pageTopDocY(target)
        val pageHeight = ctrl.pageHeightPx(target)
        panY = (ctrl.viewportHeight / 2f) - (pageTop + pageHeight / 2f) * zoom
        currentPage = target
        ctrl.clampPan()
        ctrl.requestRenderForVisiblePages()
    }

    @Suppress("unused")
    suspend fun animateScrollToPage(
        pageIndex: Int,
        animationSpec: AnimationSpec<Float> = spring()
    ) {
        val ctrl = controller ?: return
        val target = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val pageTop = ctrl.pageTopDocY(target)
        val pageHeight = ctrl.pageHeightPx(target)
        val targetPanY = (ctrl.viewportHeight / 2f) - (pageTop + pageHeight / 2f) * zoom

        currentPage = target
        Animatable(panY).animateTo(
            targetValue = targetPanY,
            animationSpec = animationSpec
        ) {
            panY = value
            ctrl.clampPan()
            ctrl.requestRenderForVisiblePages()
        }
        currentPage = target
    }

    fun setZoom(zoomLevel: Float) {
        val ctrl = controller ?: return
        val pivot = Offset(ctrl.viewportWidth / 2f, ctrl.viewportHeight / 2f)
        ctrl.onAnimatedZoomFrame(zoomLevel, pivot)
    }

    @Suppress("unused")
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

    fun zoomIn(factor: Float = 0.25f) {
        setZoom(zoom * (1f + factor))
    }

    fun zoomOut(factor: Float = 0.25f) {
        setZoom(zoom * (1f - factor))
    }

    @Suppress("unused")
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

    @Suppress("unused")
    fun setFitMode(fitMode: FitMode) {
        val ctrl = controller ?: return
        ctrl.updateConfig(ctrl.viewerConfig.copy(fitMode = fitMode))
    }

    @Suppress("unused")
    fun setScrollDirection(direction: ScrollDirection) {
        val ctrl = controller ?: return
        ctrl.updateConfig(ctrl.viewerConfig.copy(scrollDirection = direction))
    }

    @Suppress("unused")
    fun setNightMode(enabled: Boolean) {
        val ctrl = controller ?: return
        ctrl.updateConfig(ctrl.viewerConfig.copy(isNightModeEnabled = enabled))
    }

    @Suppress("unused")
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
