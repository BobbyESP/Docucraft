package com.composepdf.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import com.composepdf.state.PdfViewerController
import com.composepdf.state.PdfViewerState
import com.composepdf.state.ViewerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * State holder for gesture animations and tracking.
 */
@Stable
internal class GestureState(
    private val scope: CoroutineScope
) {
    val velocityTracker = VelocityTracker()

    // Keeps the current zoom-animation job so a new double-tap can cancel
    // a previous one that is still running.
    private var zoomAnimationJob: Job? = null

    fun trackVelocity(position: Offset) {
        velocityTracker.addPosition(System.currentTimeMillis(), position)
    }

    fun resetVelocity() {
        velocityTracker.resetTracking()
    }

    fun animateFling(
        velocity: Velocity,
        onUpdate: (Offset) -> Unit,
        onEnd: () -> Unit
    ) {
        scope.launch {
            val decaySpec = exponentialDecay<Float>()

            // Animate X
            launch {
                var lastX = 0f
                Animatable(0f).animateDecay(velocity.x, decaySpec) {
                    val delta = value - lastX
                    lastX = value
                    onUpdate(Offset(delta, 0f))
                }
            }

            // Animate Y
            launch {
                var lastY = 0f
                Animatable(0f).animateDecay(velocity.y, decaySpec) {
                    val delta = value - lastY
                    lastY = value
                    onUpdate(Offset(0f, delta))
                }
            }.invokeOnCompletion {
                onEnd()
            }
        }
    }

    /**
     * Animates both zoom and the pan offset so the [pivot] point stays
     * visually fixed during the transition.
     *
     * The trick: instead of computing the target offset up-front (which would
     * require knowing the final zoom before the animation runs), we interpolate
     * the zoom frame-by-frame and recompute the offset on every frame exactly
     * the same way [PdfViewerController.onGestureUpdate] does during a pinch.
     *
     * @param fromZoom  Starting zoom level
     * @param toZoom    Target zoom level (will be clamped by the controller)
     * @param pivot     The screen point that should remain fixed (in the coordinate
     *                  space of the layout that owns the graphicsLayer)
     * @param onFrame   Called on every animation frame with (newZoom, pivot) so the
     *                  controller can update state.zoom and state.offset consistently
     * @param onEnd     Called when the animation completes or is cancelled
     * @param animationSpec Spring spec – feels snappier than a tween for zoom
     */
    fun animateZoomAroundPivot(
        fromZoom: Float,
        toZoom: Float,
        pivot: Offset,
        onFrame: (newZoom: Float, pivot: Offset) -> Unit,
        onEnd: () -> Unit,
        animationSpec: AnimationSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 300f)
    ) {
        zoomAnimationJob?.cancel()
        zoomAnimationJob = scope.launch {
            Animatable(fromZoom).animateTo(toZoom, animationSpec) {
                onFrame(value, pivot)
            }
        }.also { job ->
            job.invokeOnCompletion { onEnd() }
        }
    }

    fun cancelZoomAnimation() {
        zoomAnimationJob?.cancel()
        zoomAnimationJob = null
    }
}

@Composable
internal fun rememberGestureState(): GestureState {
    val scope = rememberCoroutineScope()
    return remember { GestureState(scope) }
}

/**
 * Modifier extension for handling PDF viewer gestures.
 *
 * All gesture recognition (pinch, pan, double-tap, fling) is handled inside a
 * single [awaitEachGesture] block so that:
 *  1. Double-tap coordinates are in the **same coordinate space** as the
 *     graphicsLayer that applies zoom/pan (fixes the pivot offset bug).
 *  2. A single `pointerInput` handles every gesture type without conflicts.
 *
 * @param state      The PDF viewer state
 * @param controller The PDF viewer controller
 * @param config     The viewer configuration
 * @param enabled    Whether gestures are enabled
 */
@Composable
fun Modifier.pdfGestures(
    state: PdfViewerState,
    controller: PdfViewerController,
    config: ViewerConfig,
    enabled: Boolean = true
): Modifier {
    val gestureState = rememberGestureState()

    return this.pointerInput(enabled, config.isZoomGesturesEnabled) {
        if (!enabled) return@pointerInput

        // Timing constants for double-tap detection.
        val doubleTapTimeoutMs = viewConfiguration.doubleTapTimeoutMillis
        val doubleTapMinTimeMs = viewConfiguration.doubleTapMinTimeMillis

        awaitEachGesture {
            // ── First down ────────────────────────────────────────────────────
            val firstDown = awaitFirstDown(requireUnconsumed = false)
            val firstDownTime = System.currentTimeMillis()
            gestureState.resetVelocity()
            gestureState.cancelZoomAnimation()

            var zoom = 1f
            var pan = Offset.Zero
            var pastTouchSlop = false
            val touchSlop = viewConfiguration.touchSlop
            var isMultiTouch = false

            controller.onGestureStart()

            // ── Pointer loop for the first touch ──────────────────────────────
            var released = false
            do {
                val event = awaitPointerEvent()
                val canceled = event.changes.any { it.isConsumed }

                if (event.changes.size > 1) isMultiTouch = true

                if (!canceled && config.isZoomGesturesEnabled) {
                    val zoomChange = event.calculateZoom()
                    val panChange = event.calculatePan()
                    val centroid = event.calculateCentroid(useCurrent = false)

                    if (!pastTouchSlop) {
                        zoom *= zoomChange
                        pan += panChange
                        val centroidSize = pan.x * pan.x + pan.y * pan.y
                        val zoomMotion = kotlin.math.abs(1 - zoom) * size.width
                        if (centroidSize > touchSlop * touchSlop || zoomMotion > touchSlop) {
                            pastTouchSlop = true
                        }
                    }

                    if (pastTouchSlop) {
                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            controller.onGestureUpdate(zoomChange, panChange, centroid)
                        }
                        event.changes.firstOrNull()?.let { change ->
                            if (change.positionChanged()) {
                                gestureState.trackVelocity(change.position)
                            }
                        }
                        event.changes.forEach { it.consume() }
                    }
                }

                released = event.type == PointerEventType.Release
            } while (!released && !firstDown.isConsumed)

            // ── After first release: fling or check for double-tap ─────────────
            if (pastTouchSlop) {
                // The user dragged/pinched – handle fling and exit
                if (state.zoom > 1f) {
                    val velocity = gestureState.velocityTracker.calculateVelocity()
                    if (velocity.x != 0f || velocity.y != 0f) {
                        gestureState.animateFling(
                            velocity = velocity,
                            onUpdate = { delta ->
                                controller.onGestureUpdate(1f, delta, Offset.Zero)
                            },
                            onEnd = { controller.onGestureEnd() }
                        )
                    } else {
                        controller.onGestureEnd()
                    }
                } else {
                    controller.onGestureEnd()
                }
                return@awaitEachGesture
            }

            // Not a drag – check for double-tap (only for single-touch taps)
            if (isMultiTouch || !config.isZoomGesturesEnabled) {
                controller.onGestureEnd()
                return@awaitEachGesture
            }

            // Wait for a potential second down within the double-tap window
            val secondDown = withTimeoutOrNull(doubleTapTimeoutMs - (System.currentTimeMillis() - firstDownTime)) {
                awaitFirstDown(requireUnconsumed = false)
            }

            if (secondDown == null) {
                // Single tap – nothing to do for now
                controller.onGestureEnd()
                return@awaitEachGesture
            }

            // Confirm the second down happened after the minimum interval
            val elapsed = System.currentTimeMillis() - firstDownTime
            if (elapsed < doubleTapMinTimeMs) {
                controller.onGestureEnd()
                return@awaitEachGesture
            }

            // ── Double-tap confirmed ───────────────────────────────────────────
            // secondDown.position is in the coordinate space of THIS pointerInput,
            // which is the same composable that holds the graphicsLayer → correct pivot.
            val doubleTapPosition = secondDown.position

            val targetZoom = if (state.zoom < config.doubleTapZoom * 0.9f) {
                config.doubleTapZoom
            } else {
                config.minZoom
            }

            // Consume the second tap so it does not propagate
            secondDown.consume()

            gestureState.animateZoomAroundPivot(
                fromZoom = state.zoom,
                toZoom = targetZoom,
                pivot = doubleTapPosition,
                onFrame = { newZoom, pivot ->
                    // Compute the zoom change relative to the previous frame value
                    // so we can reuse the same pivot math as onGestureUpdate.
                    val zoomChange = newZoom / state.zoom
                    controller.onGestureUpdate(
                        zoomChange = zoomChange,
                        panChange = Offset.Zero,
                        pivot = pivot
                    )
                },
                onEnd = { controller.onGestureEnd() }
            )
        }
    }
}




