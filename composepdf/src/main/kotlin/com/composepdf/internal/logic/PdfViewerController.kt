package com.composepdf.internal.logic

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.composepdf.PdfSource
import com.composepdf.PdfViewerState
import com.composepdf.RenderTelemetryEvent
import com.composepdf.RenderTrigger
import com.composepdf.ViewerConfig
import com.composepdf.internal.service.cache.bitmap.BitmapPool
import com.composepdf.internal.service.renderer.PdfViewerSession
import com.composepdf.internal.util.longLivedContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.Closeable

/**
 * Main façade that orchestrates the viewer lifecycle.
 *
 * Refined to use the [BitmapPool] provided by [PdfViewerState] to ensure
 * a single source of truth for memory management.
 */
@Stable
class PdfViewerController(
    sourceContext: Context,
    val state: PdfViewerState,
    initialConfig: ViewerConfig = ViewerConfig(),
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
) : Closeable {

    val context: Context = sourceContext.longLivedContext()

    private val viewportCoordinator = ViewerViewportCoordinator(
        state = state,
        configProvider = { config }
    )

    private val viewerSession = PdfViewerSession(
        context = this.context,
        scope = scope,
        state = state,
        bitmapPool = state.bitmapPool, // Use the pool from the state
        viewportCoordinator = viewportCoordinator,
        configProvider = { initialConfig }
    )
    
    // Bridge backing session telemetry to UI state
    init {
        scope.launch {
            viewerSession.telemetrySnapshot.collectLatest { snapshot ->
                state.session.updateTelemetry(snapshot)
            }
        }
    }

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

    val renderedPages: StateFlow<Map<Int, Bitmap>> = viewerSession.renderedPages

    var config by mutableStateOf(initialConfig)
        private set

    internal val stateBridge: PdfViewerStateControllerBridge =
        object : PdfViewerStateControllerBridge {
            override val viewerConfig: ViewerConfig get() = config
            override val viewportWidth: Float get() = viewportCoordinator.viewportWidth
            override val viewportHeight: Float get() = viewportCoordinator.viewportHeight
            override val pageSizes get() = viewportCoordinator.pageSizes
            override fun pageHeightPx(index: Int): Float = viewportCoordinator.pageHeightPx(index)
            override fun pageWidthPx(index: Int): Float = viewportCoordinator.pageWidthPx(index)
            override fun pageTopDocY(index: Int): Float = viewportCoordinator.pageTopDocY(index)
            override fun pageLeftDocX(index: Int): Float = viewportCoordinator.pageLeftDocX(index)
            override fun corridorBreadth(): Float = viewportCoordinator.snapshot().corridorBreadth
            override fun visiblePageIndices(): IntRange = viewportCoordinator.visiblePageIndices()
            override fun isPointOverPage(point: Offset): Boolean =
                viewportCoordinator.isPointOverPage(point)

            override fun computeCenteredPanForPage(pageIndex: Int): Pair<Float, Float> =
                viewportCoordinator.computeCenteredPanForPage(pageIndex)

            override fun computeFitDocumentZoom(): Float =
                viewportCoordinator.computeFitDocumentZoom()

            override fun computeFitPageZoom(pageIndex: Int): Float =
                viewportCoordinator.computeFitPageZoom(pageIndex)

            override fun onViewportSizeChanged(width: Float, height: Float) =
                this@PdfViewerController.onViewportSizeChanged(width, height)

            override fun requestRenderForVisiblePages() =
                this@PdfViewerController.requestRenderForVisiblePages()

            override fun clampPan() = viewportCoordinator.clampPan()
            override fun onGestureStart() = interactionCoordinator.onGestureStart()
            override fun onGestureEnd() = interactionCoordinator.onGestureEnd()
            override fun onGestureUpdate(zoomChange: Float, panDelta: Offset, pivot: Offset) =
                interactionCoordinator.onGestureUpdate(zoomChange, panDelta, pivot)

            override fun onAnimatedZoomFrame(targetZoom: Float, pivot: Offset) =
                interactionCoordinator.onAnimatedZoomFrame(targetZoom, pivot)

            override fun updateConfig(newConfig: ViewerConfig) =
                this@PdfViewerController.updateConfig(newConfig)
        }

    internal val layoutController: ViewerLayoutController = object : ViewerLayoutController {
        override val viewportWidth: Float get() = viewportCoordinator.viewportWidth
        override val viewportHeight: Float get() = viewportCoordinator.viewportHeight
        override val pageSizes get() = viewportCoordinator.pageSizes
        override fun pageHeightPx(index: Int): Float = viewportCoordinator.pageHeightPx(index)
        override fun pageWidthPx(index: Int): Float = viewportCoordinator.pageWidthPx(index)
        override fun pageTopDocY(index: Int): Float = viewportCoordinator.pageTopDocY(index)
        override fun pageLeftDocX(index: Int): Float = viewportCoordinator.pageLeftDocX(index)
        override fun corridorBreadth(): Float = viewportCoordinator.snapshot().corridorBreadth
        override fun visiblePageIndices(): IntRange = viewportCoordinator.visiblePageIndices()
        override fun isPointOverPage(point: Offset): Boolean =
            viewportCoordinator.isPointOverPage(point)

        override fun computeCenteredPanForPage(pageIndex: Int): Pair<Float, Float> =
            viewportCoordinator.computeCenteredPanForPage(pageIndex)

        override fun computeFitDocumentZoom(): Float = viewportCoordinator.computeFitDocumentZoom()
        override fun computeFitPageZoom(pageIndex: Int): Float =
            viewportCoordinator.computeFitPageZoom(pageIndex)

        override fun onViewportSizeChanged(width: Float, height: Float) =
            this@PdfViewerController.onViewportSizeChanged(width, height)

        override fun requestRenderForVisiblePages() =
            this@PdfViewerController.requestRenderForVisiblePages()

        override fun clampPan() = viewportCoordinator.clampPan()
    }

    internal val gestureController: ViewerGestureController = object : ViewerGestureController {
        override val viewportWidth: Float get() = viewportCoordinator.viewportWidth
        override val viewportHeight: Float get() = viewportCoordinator.viewportHeight
        override val pageSizes get() = viewportCoordinator.pageSizes
        override fun pageHeightPx(index: Int): Float = viewportCoordinator.pageHeightPx(index)
        override fun pageWidthPx(index: Int): Float = viewportCoordinator.pageWidthPx(index)
        override fun pageTopDocY(index: Int): Float = viewportCoordinator.pageTopDocY(index)
        override fun pageLeftDocX(index: Int): Float = viewportCoordinator.pageLeftDocX(index)
        override fun corridorBreadth(): Float = viewportCoordinator.snapshot().corridorBreadth
        override fun visiblePageIndices(): IntRange = viewportCoordinator.visiblePageIndices()
        override fun isPointOverPage(point: Offset): Boolean =
            viewportCoordinator.isPointOverPage(point)

        override fun computeCenteredPanForPage(pageIndex: Int): Pair<Float, Float> =
            viewportCoordinator.computeCenteredPanForPage(pageIndex)

        override fun computeFitDocumentZoom(): Float = viewportCoordinator.computeFitDocumentZoom()
        override fun computeFitPageZoom(pageIndex: Int): Float =
            viewportCoordinator.computeFitPageZoom(pageIndex)

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

    fun updateConfig(newConfig: ViewerConfig) {
        if (config == newConfig) return
        val previousConfig = config
        config = newConfig
        sessionCoordinator.onConfigChanged(previousConfig, newConfig)
    }

    internal fun onViewportSizeChanged(width: Float, height: Float) {
        sessionCoordinator.onViewportSizeChanged(width, height)
    }

    fun loadDocument(source: PdfSource) {
        sessionCoordinator.loadDocument(source)
    }

    @Suppress("unused")
    fun recentRenderEvents(limit: Int = 50): List<RenderTelemetryEvent> =
        viewerSession.recentRenderEvents(limit)

    internal fun requestRenderForVisiblePages() {
        viewerSession.requestRenderForVisiblePages(RenderTrigger.PROGRAMMATIC)
    }

    override fun close() {
        viewerSession.close()
        scope.cancel()
    }
}
