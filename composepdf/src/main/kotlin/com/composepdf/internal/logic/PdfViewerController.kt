/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
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
import com.composepdf.ViewerConfig
import com.composepdf.internal.engine.PlanInputs
import com.composepdf.internal.engine.RenderEngine
import com.composepdf.internal.engine.TileDraw
import com.composepdf.internal.service.pdf.PdfDocumentManager
import com.composepdf.internal.service.pdf.PdfDocumentSession
import com.composepdf.internal.util.longLivedContext
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates the viewer: document lifecycle, gesture handling, viewport geometry and the render
 * engine. This is the only mutable hub; everything below it is either pure geometry
 * ([ViewerViewportCoordinator]/[PageLayoutSnapshot]) or the self-contained [RenderEngine].
 */
@Stable
internal class PdfViewerController(
    sourceContext: Context,
    val state: PdfViewerState,
    initialConfig: ViewerConfig = ViewerConfig(),
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
) : Closeable, ViewerController {

    val context: Context = sourceContext.longLivedContext()

    var config by mutableStateOf(initialConfig)
        private set

    private val viewportCoordinator =
        ViewerViewportCoordinator(state = state, configProvider = { config })

    private val documentManager = PdfDocumentManager(context)
    private val documentSession = PdfDocumentSession(context, documentManager)

    private val engine =
        RenderEngine(
            documentManager = documentManager,
            pool = state.bitmapPool,
            inputsProvider = ::currentPlanInputs,
        )

    /** Base page bitmaps, observed by the UI. */
    val renderedPages: StateFlow<Map<Int, Bitmap>> = engine.baseBitmaps

    /** High-resolution tiles per page, observed by the UI. */
    val tiles: StateFlow<Map<Int, List<TileDraw>>> = engine.tiles

    private fun currentPlanInputs(): PlanInputs? {
        if (!viewportCoordinator.hasLayout) return null
        val currentConfig = config
        return PlanInputs(
            layout = viewportCoordinator.snapshot(),
            panX = state.panX,
            panY = state.panY,
            zoom = state.zoom,
            viewportWidth = viewportCoordinator.viewportWidth,
            viewportHeight = viewportCoordinator.viewportHeight,
            velocityX = state.scrollVelocity.x,
            velocityY = state.scrollVelocity.y,
            renderQuality = currentConfig.renderQuality,
            prefetchDistance = currentConfig.prefetchDistance,
        )
    }

    // ------------------------------------------------------------------ document lifecycle

    fun loadDocument(source: PdfSource) {
        scope.launch {
            state.reset()
            engine.invalidate()
            try {
                val document = documentSession.open(source, state::updateRemoteDocumentState)
                viewportCoordinator.updatePageSizes(document.pageSizes)
                state.completeDocumentLoad(document.pageCount)
                engine.requestPlan()
            } catch (error: Exception) {
                state.failDocumentLoad(error)
            }
        }
    }

    // ------------------------------------------------------------------ ViewerController

    override val viewerConfig: ViewerConfig
        get() = config

    override val viewportWidth: Float
        get() = viewportCoordinator.viewportWidth

    override val viewportHeight: Float
        get() = viewportCoordinator.viewportHeight

    override fun pageWidthPx(index: Int): Float = viewportCoordinator.pageWidthPx(index)

    override fun pageHeightPx(index: Int): Float = viewportCoordinator.pageHeightPx(index)

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

    override fun clampPan() = viewportCoordinator.clampPan()

    override fun requestRenderForVisiblePages() {
        viewportCoordinator.updateCurrentPageFromViewport()
        engine.requestPlan()
    }

    override fun onViewportSizeChanged(width: Float, height: Float) {
        val before = viewportCoordinator.snapshot()
        if (!viewportCoordinator.updateViewport(width, height)) return
        val after = viewportCoordinator.snapshot()
        // Cached bitmaps depend on page layout sizes, not on the viewport itself. Keep them when
        // only the window changed (e.g. animated insets) and rebuild when pages resized.
        val layoutUnchanged =
            before.pageWidths.contentEquals(after.pageWidths) &&
                before.pageHeights.contentEquals(after.pageHeights)
        if (layoutUnchanged) {
            engine.requestPlan()
        } else {
            engine.invalidate()
        }
    }

    override fun updateConfig(newConfig: ViewerConfig) {
        if (config == newConfig) return
        val previous = config
        config = newConfig

        val layoutChanged =
            previous.fitMode != newConfig.fitMode ||
                previous.pageSpacingPx != newConfig.pageSpacingPx ||
                previous.scrollDirection != newConfig.scrollDirection
        if (layoutChanged) {
            viewportCoordinator.onLayoutInputsChanged()
            engine.invalidate()
        } else if (previous.renderQuality != newConfig.renderQuality) {
            engine.invalidate()
        } else {
            engine.requestPlan()
        }
    }

    // ------------------------------------------------------------------ gestures

    override fun onGestureStart() {
        state.isGestureActive = true
    }

    override fun onGestureEnd() {
        state.isGestureActive = false
        viewportCoordinator.clampPan()
        viewportCoordinator.updateCurrentPageFromViewport()
        engine.requestPlan()
    }

    override fun onGestureUpdate(zoomChange: Float, panDelta: Offset, pivot: Offset) {
        if (viewportCoordinator.viewportWidth == 0f) return
        val currentConfig = config
        val nextZoom =
            (state.zoom * zoomChange).coerceIn(currentConfig.minZoom, currentConfig.maxZoom)
        applyZoomAroundPivot(nextZoom, pivot)

        state.panX += panDelta.x
        state.panY += panDelta.y
        viewportCoordinator.clampPan()
        viewportCoordinator.updateCurrentPageFromViewport()
        engine.requestPlan()
    }

    override fun onAnimatedZoomFrame(targetZoom: Float, pivot: Offset) {
        val currentConfig = config
        val nextZoom = targetZoom.coerceIn(currentConfig.minZoom, currentConfig.maxZoom)
        if (!applyZoomAroundPivot(nextZoom, pivot)) return
        viewportCoordinator.clampPan()
        viewportCoordinator.updateCurrentPageFromViewport()
        engine.requestPlan()
    }

    private fun applyZoomAroundPivot(targetZoom: Float, pivot: Offset): Boolean {
        val previousZoom = state.zoom
        if (targetZoom == previousZoom) return false
        val ratio = targetZoom / previousZoom
        state.panX = pivot.x + (state.panX - pivot.x) * ratio
        state.panY = pivot.y + (state.panY - pivot.y) * ratio
        state.zoom = targetZoom
        return true
    }

    override fun close() {
        engine.close()
        documentManager.close()
        scope.cancel()
    }
}
