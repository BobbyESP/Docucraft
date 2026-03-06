package com.composepdf.state

import androidx.compose.ui.geometry.Offset
import com.composepdf.renderer.RenderTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coordinates interaction state transitions for the viewer.
 *
 * It owns the mutable gesture/animation flow that used to live in `PdfViewerController`:
 * - gesture start/end lifecycle
 * - zooming around a pivot
 * - pan accumulation
 * - render debouncing during drag and animated zoom
 *
 * This keeps `PdfViewerController` as a façade while preserving the same public methods.
 */
internal class ViewerInteractionCoordinator(
    private val scope: CoroutineScope,
    private val state: PdfViewerState,
    private val configProvider: () -> ViewerConfig,
    private val viewportCoordinator: ViewerViewportCoordinator,
    private val recordPanDelta: (Float) -> Unit,
    private val requestRender: (RenderTrigger) -> Unit,
    private val debounceDelay: suspend (Long) -> Unit = { delay(it) }
) {
    private var gestureRenderJob: Job? = null
    private var animatedZoomRenderJob: Job? = null

    fun onGestureStart() {
        state.isGestureActive = true
    }

    fun onGestureEnd() {
        state.isGestureActive = false
        viewportCoordinator.clampPan()
        viewportCoordinator.updateCurrentPageFromViewport()
        requestRender(RenderTrigger.GESTURE_END)
    }

    fun onGestureUpdate(zoomChange: Float, panDelta: Offset, pivot: Offset) {
        if (viewportCoordinator.viewportWidth == 0f) return

        val currentConfig = configProvider()
        val previousZoom = state.zoom
        val nextZoom = (previousZoom * zoomChange).coerceIn(currentConfig.minZoom, currentConfig.maxZoom)
        val isZooming = applyZoomAroundPivot(nextZoom, pivot)

        recordPanDelta(panDelta.y)
        state.panX += panDelta.x
        state.panY += panDelta.y
        viewportCoordinator.clampPan()
        viewportCoordinator.updateCurrentPageFromViewport()

        // Increased debounce slightly during active gestures to avoid queueing too many
        // concurrent render passes while the user is still moving the viewport.
        debounceGestureRender(if (isZooming) 200L else 120L)
    }

    fun onAnimatedZoomFrame(targetZoom: Float, pivot: Offset) {
        val currentConfig = configProvider()
        val nextZoom = targetZoom.coerceIn(currentConfig.minZoom, currentConfig.maxZoom)
        val changed = applyZoomAroundPivot(nextZoom, pivot)
        if (!changed) return

        viewportCoordinator.clampPan()
        viewportCoordinator.updateCurrentPageFromViewport()

        animatedZoomRenderJob?.cancel()
        animatedZoomRenderJob = scope.launch {
            // Animating is very high-churn, increase debounce.
            debounceDelay(150L)
            requestRender(RenderTrigger.ANIMATED_ZOOM_SETTLED)
        }
    }

    private fun debounceGestureRender(delayMs: Long) {
        gestureRenderJob?.cancel()
        gestureRenderJob = scope.launch {
            debounceDelay(delayMs)
            requestRender(RenderTrigger.GESTURE_DEBOUNCED)
        }
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

    private companion object {
        const val PAN_GESTURE_DEBOUNCE_MS = 80L
        const val ZOOM_GESTURE_DEBOUNCE_MS = 150L
        const val ANIMATED_ZOOM_DEBOUNCE_MS = 120L
    }
}
