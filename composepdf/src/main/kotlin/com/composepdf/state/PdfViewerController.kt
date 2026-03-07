package com.composepdf.state

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.composepdf.cache.BitmapPool
import com.composepdf.renderer.PdfViewerSession
import com.composepdf.renderer.RenderTelemetryEvent
import com.composepdf.renderer.RenderTelemetrySnapshot
import com.composepdf.renderer.RenderTrigger
import com.composepdf.source.PdfSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable

/**
 * Main façade that orchestrates the viewer lifecycle.
 *
 * The controller no longer acts as the direct dependency of every UI concern. Instead it wires and
 * sequences specialized collaborators, then exposes narrow internal contracts for:
 * - public imperative state APIs ([stateBridge])
 * - layout/viewport integration ([layoutController])
 * - gesture handling ([gestureController])
 *
 * This keeps document loading, viewport math, gesture processing and render execution easier to
 * isolate when debugging or extending the viewer.
 */
@Stable
class PdfViewerController(
    val context: Context,
    val state: PdfViewerState,
    initialConfig: ViewerConfig = ViewerConfig(),
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
    private val bitmapPool: BitmapPool = BitmapPool()
) : Closeable {

    constructor(context: Context, state: PdfViewerState, config: ViewerConfig) : this(
        context = context,
        state = state,
        initialConfig = config
    )

    private val viewportCoordinator = ViewerViewportCoordinator(
        state = state,
        configProvider = { config }
    )
    private val viewerSession = PdfViewerSession(
        context = context,
        scope = scope,
        state = state,
        bitmapPool = bitmapPool,
        viewportCoordinator = viewportCoordinator,
        configProvider = { config }
    )
    private val interactionCoordinator = ViewerInteractionCoordinator(
        scope = scope,
        state = state,
        configProvider = { config },
        viewportCoordinator = viewportCoordinator,
        recordPanDelta = viewerSession::recordPanDelta,
        requestRender = viewerSession::requestRenderForVisiblePages
    )
    private val sessionCoordinator = ViewerSessionCoordinator(
        scope = scope,
        state = state,
        viewportCoordinator = viewportCoordinator,
        updatePrefetchWindow = viewerSession::updatePrefetchWindow,
        invalidateAll = viewerSession::invalidateAll,
        invalidateTiles = viewerSession::invalidateTiles,
        loadDocument = viewerSession::loadDocument,
        requestRender = viewerSession::requestRenderForVisiblePages
    )

    /** Map of page indices to rendered low-resolution base bitmaps. */
    val renderedPages: StateFlow<Map<Int, Bitmap>> = viewerSession.renderedPages

    /** Read-only snapshot of the internal render telemetry for diagnostics. */
    @Suppress("unused")
    val renderTelemetry: StateFlow<RenderTelemetrySnapshot> = viewerSession.renderTelemetry

    var config by mutableStateOf(initialConfig)
        private set

    /** Facade consumed by [PdfViewerState] so programmatic APIs do not depend on the controller implementation. */
    internal val stateBridge: PdfViewerStateControllerBridge = object : PdfViewerStateControllerBridge {
        override val viewerConfig: ViewerConfig get() = config
        override val viewportWidth: Float get() = viewportCoordinator.viewportWidth
        override val viewportHeight: Float get() = viewportCoordinator.viewportHeight
        override val pageSizes get() = viewportCoordinator.pageSizes
        override fun pageHeightPx(index: Int): Float = viewportCoordinator.pageHeightPx(index)
        override fun pageWidthPx(index: Int): Float = viewportCoordinator.pageWidthPx(index)
        override fun pageTopDocY(index: Int): Float = viewportCoordinator.pageTopDocY(index)
        override fun visiblePageIndices(): IntRange = viewportCoordinator.visiblePageIndices()
        override fun isPointOverPage(point: Offset): Boolean = viewportCoordinator.isPointOverPage(point)
        override fun computeCenteredPanForPage(pageIndex: Int): Pair<Float, Float> =
            viewportCoordinator.computeCenteredPanForPage(pageIndex)
        override fun computeFitDocumentZoom(): Float = viewportCoordinator.computeFitDocumentZoom()
        override fun computeFitPageZoom(pageIndex: Int): Float = viewportCoordinator.computeFitPageZoom(pageIndex)
        override fun onViewportSizeChanged(width: Float, height: Float) =
            this@PdfViewerController.onViewportSizeChanged(width, height)
        override fun requestRenderForVisiblePages() = this@PdfViewerController.requestRenderForVisiblePages()
        override fun clampPan() = viewportCoordinator.clampPan()
        override fun onGestureStart() = interactionCoordinator.onGestureStart()
        override fun onGestureEnd() = interactionCoordinator.onGestureEnd()
        override fun onGestureUpdate(zoomChange: Float, panDelta: Offset, pivot: Offset) =
            interactionCoordinator.onGestureUpdate(zoomChange, panDelta, pivot)
        override fun onAnimatedZoomFrame(targetZoom: Float, pivot: Offset) =
            interactionCoordinator.onAnimatedZoomFrame(targetZoom, pivot)
        override fun updateConfig(newConfig: ViewerConfig) = this@PdfViewerController.updateConfig(newConfig)
    }

    /** Layout-facing contract used by the core layout instead of the full controller. */
    internal val layoutController: ViewerLayoutController = object : ViewerLayoutController {
        override val viewportWidth: Float get() = viewportCoordinator.viewportWidth
        override val viewportHeight: Float get() = viewportCoordinator.viewportHeight
        override val pageSizes get() = viewportCoordinator.pageSizes
        override fun pageHeightPx(index: Int): Float = viewportCoordinator.pageHeightPx(index)
        override fun pageWidthPx(index: Int): Float = viewportCoordinator.pageWidthPx(index)
        override fun pageTopDocY(index: Int): Float = viewportCoordinator.pageTopDocY(index)
        override fun visiblePageIndices(): IntRange = viewportCoordinator.visiblePageIndices()
        override fun isPointOverPage(point: Offset): Boolean = viewportCoordinator.isPointOverPage(point)
        override fun computeCenteredPanForPage(pageIndex: Int): Pair<Float, Float> =
            viewportCoordinator.computeCenteredPanForPage(pageIndex)
        override fun computeFitDocumentZoom(): Float = viewportCoordinator.computeFitDocumentZoom()
        override fun computeFitPageZoom(pageIndex: Int): Float = viewportCoordinator.computeFitPageZoom(pageIndex)
        override fun onViewportSizeChanged(width: Float, height: Float) =
            this@PdfViewerController.onViewportSizeChanged(width, height)
        override fun requestRenderForVisiblePages() = this@PdfViewerController.requestRenderForVisiblePages()
        override fun clampPan() = viewportCoordinator.clampPan()
    }

    /** Gesture-facing contract so the pointer modifier only sees interaction-specific capabilities. */
    internal val gestureController: ViewerGestureController = object : ViewerGestureController {
        override val viewportWidth: Float get() = viewportCoordinator.viewportWidth
        override val viewportHeight: Float get() = viewportCoordinator.viewportHeight
        override val pageSizes get() = viewportCoordinator.pageSizes
        override fun pageHeightPx(index: Int): Float = viewportCoordinator.pageHeightPx(index)
        override fun pageWidthPx(index: Int): Float = viewportCoordinator.pageWidthPx(index)
        override fun pageTopDocY(index: Int): Float = viewportCoordinator.pageTopDocY(index)
        override fun visiblePageIndices(): IntRange = viewportCoordinator.visiblePageIndices()
        override fun isPointOverPage(point: Offset): Boolean = viewportCoordinator.isPointOverPage(point)
        override fun computeCenteredPanForPage(pageIndex: Int): Pair<Float, Float> =
            viewportCoordinator.computeCenteredPanForPage(pageIndex)
        override fun computeFitDocumentZoom(): Float = viewportCoordinator.computeFitDocumentZoom()
        override fun computeFitPageZoom(pageIndex: Int): Float = viewportCoordinator.computeFitPageZoom(pageIndex)
        override fun onGestureStart() = interactionCoordinator.onGestureStart()
        override fun onGestureEnd() = interactionCoordinator.onGestureEnd()
        override fun onGestureUpdate(zoomChange: Float, panDelta: Offset, pivot: Offset) =
            interactionCoordinator.onGestureUpdate(zoomChange, panDelta, pivot)
        override fun onAnimatedZoomFrame(targetZoom: Float, pivot: Offset) =
            interactionCoordinator.onAnimatedZoomFrame(targetZoom, pivot)
    }

    init {
        viewerSession.updatePrefetchWindow(config.prefetchDistance)
    }

    /** Updates viewer configuration and refreshes renders if necessary. */
    fun updateConfig(newConfig: ViewerConfig) {
        if (config == newConfig) return

        val previousConfig = config
        config = newConfig
        sessionCoordinator.onConfigChanged(previousConfig, newConfig)
    }

    /** Updates viewport size and rebuilds the document layout snapshot. */
    internal fun onViewportSizeChanged(width: Float, height: Float) {
        sessionCoordinator.onViewportSizeChanged(width, height)
    }

    /** Loads a PDF from a [PdfSource] while keeping document-session mutations grouped in [PdfViewerState]. */
    fun loadDocument(source: PdfSource) {
        sessionCoordinator.loadDocument(source)
    }

    /** Recent render events in chronological order, useful for local diagnostics. */
    @Suppress("unused")
    fun recentRenderEvents(limit: Int = 50): List<RenderTelemetryEvent> =
        viewerSession.recentRenderEvents(limit)

    /** Triggers rendering for base pages and high-res tiles for the current viewport. */
    internal fun requestRenderForVisiblePages() {
        requestRenderForVisiblePages(RenderTrigger.PROGRAMMATIC)
    }

    private fun requestRenderForVisiblePages(trigger: RenderTrigger) {
        viewerSession.requestRenderForVisiblePages(trigger)
    }

    override fun close() {
        scope.cancel()
        viewerSession.close()
    }
}
