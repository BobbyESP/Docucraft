package com.composepdf.internal.logic

import androidx.compose.ui.geometry.Offset
import com.composepdf.PdfViewerState
import com.composepdf.RenderTrigger
import com.composepdf.ScrollDirection
import com.composepdf.ViewerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coordinates interaction state transitions and gesture logic for the PDF viewer.
 *
 * This class manages the mutable state flow associated with user input, including:
 * - Tracking the gesture lifecycle (start/end/update).
 * - Calculating zoom levels relative to a specific pivot point.
 * - Accumulating and clamping pan offsets within the viewport bounds.
 * - Managing render debouncing to ensure performance during high-churn operations like
 *   active dragging or animated transitions.
 *
 * It acts as the internal logic engine for [PdfViewerController], abstracting viewport
 * manipulation and rendering triggers away from the public API.
 *
 * @property scope The coroutine scope used for launching debounced render jobs.
 * @property state The mutable state of the PDF viewer being managed.
 * @property configProvider A provider for the current [ViewerConfig], used to access zoom limits and scroll direction.
 * @property viewportCoordinator Coordinator responsible for clamping offsets and mapping the viewport to pages.
 * @property recordPanDelta Callback to record the displacement for specific interaction tracking.
 * @property requestRender Callback to trigger a new render pass via a [RenderTrigger].
 * @property debounceDelay The delay mechanism used for debouncing, defaults to standard coroutine delay.
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
        val nextZoom =
            (previousZoom * zoomChange).coerceIn(currentConfig.minZoom, currentConfig.maxZoom)
        val isZooming = applyZoomAroundPivot(nextZoom, pivot)

        val delta =
            if (currentConfig.scrollDirection == ScrollDirection.VERTICAL) panDelta.y else panDelta.x
        recordPanDelta(delta)

        state.panX += panDelta.x
        state.panY += panDelta.y
        viewportCoordinator.clampPan()
        viewportCoordinator.updateCurrentPageFromViewport()

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