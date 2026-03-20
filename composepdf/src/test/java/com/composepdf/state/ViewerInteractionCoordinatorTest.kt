package com.composepdf.state

import android.util.Size
import androidx.compose.ui.geometry.Offset
import com.composepdf.PdfViewerState
import com.composepdf.ViewerConfig
import com.composepdf.internal.logic.PageLayoutSnapshot
import com.composepdf.internal.logic.ViewerInteractionCoordinator
import com.composepdf.internal.logic.ViewerViewportCoordinator
import com.composepdf.internal.logic.ViewportMetrics
import com.composepdf.internal.service.renderer.RenderTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerInteractionCoordinatorTest {

    @Test
    fun onGestureStart_andEnd_toggleGestureStateAndRequestRender() {
        val state = configuredState(pageCount = 2)
        val viewportCoordinator = viewportCoordinator(state)
        var renderRequests = 0
        var lastTrigger: RenderTrigger? = null
        val coordinator = ViewerInteractionCoordinator(
            scope = CoroutineScope(Dispatchers.Unconfined),
            state = state,
            configProvider = { ViewerConfig() },
            viewportCoordinator = viewportCoordinator,
            recordPanDelta = {},
            requestRender = { trigger -> renderRequests++; lastTrigger = trigger },
            debounceDelay = {}
        )

        viewportCoordinator.updatePageSizes(List(2) { Size(1, 1) })
        viewportCoordinator.updateViewport(500f, 500f)

        coordinator.onGestureStart()
        assertTrue(state.isGestureActive)

        coordinator.onGestureEnd()
        assertFalse(state.isGestureActive)
        assertEquals(1, renderRequests)
        assertEquals(RenderTrigger.GESTURE_END, lastTrigger)
    }

    @Test
    fun onGestureUpdate_appliesZoomPan_andRequestsDebouncedRender() {
        val state = configuredState(pageCount = 2)
        val viewportCoordinator = viewportCoordinator(state)
        var recordedPan = 0f
        var renderRequests = 0
        var lastTrigger: RenderTrigger? = null
        val coordinator = ViewerInteractionCoordinator(
            scope = CoroutineScope(Dispatchers.Unconfined),
            state = state,
            configProvider = { ViewerConfig(minZoom = 1f, maxZoom = 5f) },
            viewportCoordinator = viewportCoordinator,
            recordPanDelta = { recordedPan = it },
            requestRender = { trigger -> renderRequests++; lastTrigger = trigger },
            debounceDelay = {}
        )

        viewportCoordinator.updatePageSizes(List(2) { Size(1, 1) })
        viewportCoordinator.updateViewport(500f, 500f)

        coordinator.onGestureUpdate(
            zoomChange = 1.5f,
            panDelta = Offset(-30f, -40f),
            pivot = Offset(250f, 250f)
        )

        assertEquals(-40f, recordedPan, 0.001f)
        assertEquals(1.5f, state.zoom, 0.001f)
        assertEquals(1, renderRequests)
        assertEquals(RenderTrigger.GESTURE_DEBOUNCED, lastTrigger)
    }

    @Test
    fun onAnimatedZoomFrame_updatesZoomAndRequestsRender() {
        val state = configuredState(pageCount = 1)
        val viewportCoordinator = viewportCoordinator(state)
        var renderRequests = 0
        var lastTrigger: RenderTrigger? = null
        val coordinator = ViewerInteractionCoordinator(
            scope = CoroutineScope(Dispatchers.Unconfined),
            state = state,
            configProvider = { ViewerConfig(minZoom = 1f, maxZoom = 4f) },
            viewportCoordinator = viewportCoordinator,
            recordPanDelta = {},
            requestRender = { trigger -> renderRequests++; lastTrigger = trigger },
            debounceDelay = {}
        )

        viewportCoordinator.updatePageSizes(List(1) { Size(1, 1) })
        viewportCoordinator.updateViewport(500f, 500f)

        coordinator.onAnimatedZoomFrame(
            targetZoom = 2f,
            pivot = Offset(250f, 250f)
        )

        assertEquals(2f, state.zoom, 0.001f)
        assertEquals(1, renderRequests)
        assertEquals(RenderTrigger.ANIMATED_ZOOM_SETTLED, lastTrigger)
    }

    private fun configuredState(pageCount: Int) = PdfViewerState().apply {
        this.pageCount = pageCount
        zoom = 1f
        panX = 0f
        panY = 0f
    }

    private fun viewportCoordinator(state: PdfViewerState) = ViewerViewportCoordinator(
        state = state,
        configProvider = { ViewerConfig() },
        snapshotFactory = { pageSizes, viewportWidth, viewportHeight, _, pageSpacingPx, scrollDirection ->
            PageLayoutSnapshot(
                pageSizes = List(pageSizes.size) { Size(1, 1) },
                pageOffsets = FloatArray(pageSizes.size) { index -> index * 520f },
                pageHeights = FloatArray(pageSizes.size) { 500f },
                pageWidths = FloatArray(pageSizes.size) { 500f },
                totalDocumentSize = if (pageSizes.isEmpty()) 0f else (pageSizes.size * 500f) + ((pageSizes.size - 1) * 20f),
                corridorBreadth = 500f,
                viewport = ViewportMetrics(viewportWidth, viewportHeight),
                pageSpacingPx = pageSpacingPx,
                scrollDirection = scrollDirection
            )
        }
    )
}
