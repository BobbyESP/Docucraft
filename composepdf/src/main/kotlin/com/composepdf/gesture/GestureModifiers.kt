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
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Velocity
import com.composepdf.state.PdfViewerController
import com.composepdf.state.PdfViewerState
import com.composepdf.state.ViewerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// ── GestureState ─────────────────────────────────────────────────────────────

/**
 * Holds coroutine-based animation jobs for fling and zoom animations.
 *
 * Kept separate from [PdfViewerController] so the gesture modifier can cancel
 * in-progress animations immediately when a new finger touches down, without
 * needing direct access to the controller's coroutine scope.
 *
 * Annotated [@Stable] so Compose knows its fields don't change identity across
 * recompositions — the [rememberGestureState] factory guarantees a single instance
 * per composition site.
 */
@Stable
internal class GestureState(private val scope: CoroutineScope) {

    val velocityTracker = VelocityTracker()
    private var flingJob: Job? = null
    private var animJob: Job? = null

    /** Resets velocity tracking at the start of each new gesture. */
    fun reset() = velocityTracker.resetTracking()

    /**
     * Cancels any in-progress fling or zoom animation.
     * Called on finger-down so a new gesture always starts from a clean state.
     */
    fun cancelAll() {
        flingJob?.cancel()
        animJob?.cancel()
    }

    /**
     * Runs an exponential-decay fling animation on both axes in parallel.
     *
     * Each axis is animated independently (separate coroutines) so that horizontal
     * and vertical deceleration can differ naturally with the decay curve.
     * [onDelta] receives incremental position changes; [onEnd] is called once
     * both axes have come to rest.
     */
    fun fling(velocity: Velocity, onDelta: (Offset) -> Unit, onEnd: () -> Unit) {
        flingJob?.cancel()
        flingJob = scope.launch {
            val decay = exponentialDecay<Float>(frictionMultiplier = 1.15f)
            val jx = launch {
                var last = 0f
                Animatable(0f).animateDecay(velocity.x, decay) {
                    onDelta(Offset(value - last, 0f)); last = value
                }
            }
            val jy = launch {
                var last = 0f
                Animatable(0f).animateDecay(velocity.y, decay) {
                    onDelta(Offset(0f, value - last)); last = value
                }
            }
            jx.join(); jy.join()
            onEnd()
        }
    }

    /**
     * Runs a spring-interpolated zoom animation from [from] to [to].
     *
     * [onFrame] is called on every animation frame with the current absolute zoom
     * value and the fixed [pivot] point. Using absolute values (not incremental deltas)
     * avoids floating-point drift that accumulates across many small multiplications.
     */
    fun animateZoom(
        from: Float, to: Float, pivot: Offset,
        onFrame: (zoom: Float, pivot: Offset) -> Unit,
        onEnd: () -> Unit,
        spec: AnimationSpec<Float> = spring(dampingRatio = 0.72f, stiffness = 420f)
    ) {
        animJob?.cancel()
        animJob = scope.launch {
            Animatable(from).animateTo(to, spec) { onFrame(value, pivot) }
            onEnd()
        }
    }
}

@Composable
internal fun rememberGestureState(): GestureState {
    val scope = rememberCoroutineScope()
    return remember { GestureState(scope) }
}

/**
 * Unified PDF gesture [Modifier] handling pinch-zoom, pan, fling, and double-tap.
 *
 * ## Gesture phases
 *
 * 1. **Finger down** — cancels any in-progress fling/animation, resets velocity tracker.
 * 2. **Move loop** — accumulates pan/zoom until touch-slop threshold is exceeded, then
 *    forwards every frame to [PdfViewerController.onGestureUpdate].
 * 3. **Finger up** — if velocity exceeds 1000 px/s a fling animation is started;
 *    otherwise [PdfViewerController.onGestureEnd] is called immediately.
 * 4. **Double-tap** — detected only when no slop was exceeded (i.e. the gesture was
 *    a tap, not a drag). A second touch must arrive within [doubleTapTimeout] and
 *    within [doubleTapRadius] of the first. Triggers a spring-animated zoom toggle.
 *
 * ## Touch-slop handling
 *
 * Events are not forwarded to the controller until the accumulated pan (in pixels)
 * or zoom (converted to pixels via viewport width) exceeds [androidx.compose.ui.platform.ViewConfiguration.touchSlop].
 * This prevents micro-jitter from triggering scrolls during taps.
 *
 * ## Why [awaitEachGesture] instead of [androidx.compose.foundation.gestures.detectTransformGestures]?
 *
 * `detectTransformGestures` does not expose the raw pointer stream needed for
 * double-tap detection and velocity tracking. [awaitEachGesture] gives us full
 * control over the pointer event loop.
 */
@Composable
fun Modifier.pdfGestures(
    state: PdfViewerState,
    controller: PdfViewerController,
    config: ViewerConfig,
    enabled: Boolean = true
): Modifier {
    val gs = rememberGestureState()
    val viewConfiguration = androidx.compose.ui.platform.LocalViewConfiguration.current

    return this.pointerInput(enabled) {
        if (!enabled) return@pointerInput

        val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
        val touchSlop = viewConfiguration.touchSlop
        val doubleTapRadius = 100f // Explicit radius for reliable detection

        awaitEachGesture {
            // ── 1. Wait for first finger down ─────────────────────────────
            val firstDown = awaitFirstDown(requireUnconsumed = false)
            val firstDownTime = System.currentTimeMillis()
            val firstDownPos = firstDown.position

            // Stop any ongoing animation immediately
            gs.cancelAll()
            gs.reset()
            controller.onGestureStart()

            var zooming = false
            var pastSlop = false
            var accPan = Offset.Zero
            var accZoom = 1f

            // Track the primary pointer for velocity (scrolling)
            var velocityTrackerId = firstDown.id
            gs.velocityTracker.addPointerInputChange(firstDown)

            // ── 2. Main gesture loop ──────────────────────────────────────
            var event: PointerEvent
            var canceled = false

            do {
                event = awaitPointerEvent()
                val changes = event.changes
                val changeCount = changes.size

                // If the tracked pointer is lifted, pick another one
                if (changes.none { it.id == velocityTrackerId && it.pressed }) {
                    val newPrimary = changes.firstOrNull { it.pressed }
                    if (newPrimary != null) {
                        velocityTrackerId = newPrimary.id
                        gs.velocityTracker.resetTracking() // Reset velocity on pointer switch
                    }
                }

                // Check for multi-touch (zooming)
                val pressedCount = changes.count { it.pressed }
                if (pressedCount > 1) {
                    zooming = true
                }

                val zoomDelta = event.calculateZoom()
                val panDelta = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = false)

                if (!pastSlop) {
                    accZoom *= zoomDelta
                    accPan += panDelta

                    val panDistSq = accPan.getDistanceSquared()
                    val zoomDist = kotlin.math.abs(1f - accZoom)

                    // Check if we exceeded slop
                    if (panDistSq > touchSlop * touchSlop || zoomDist > 0.05f) {
                        pastSlop = true
                        // Apply the accumulated delta immediately so it doesn't jump
                        controller.onGestureUpdate(accZoom, accPan, centroid)
                    }
                } else {
                    // Apply changes directly
                    if (zoomDelta != 1f || panDelta != Offset.Zero) {
                        controller.onGestureUpdate(zoomDelta, panDelta, centroid)
                    }
                }

                // Track velocity
                val trackedChange = changes.firstOrNull { it.id == velocityTrackerId }
                if (trackedChange != null && trackedChange.positionChanged()) {
                    gs.velocityTracker.addPointerInputChange(trackedChange)
                }

                // Consume events if we are dragging/zooming to prevent other components from stealing
                if (pastSlop) {
                    changes.forEach {
                        if (it.positionChanged()) it.consume()
                    }
                }

                if (changes.all { !it.pressed }) {
                    canceled = true
                }

            } while (!canceled && event.changes.any { it.pressed })

            // ── 3. Gesture Finished ───────────────────────────────────────

            if (pastSlop) {
                // It was a drag/zoom
                if (zooming) {
                    // Zoom end - check if we need to snap or just stop
                    controller.onGestureEnd()
                } else {
                    val velocity = gs.velocityTracker.calculateVelocity()
                    val maxVelocity = viewConfiguration.maximumFlingVelocity
                    val minVelocity = viewConfiguration.minimumFlingVelocity

                    val velocityX = velocity.x.coerceIn(-maxVelocity, maxVelocity)
                    val velocityY = velocity.y.coerceIn(-maxVelocity, maxVelocity)
                    val speed = kotlin.math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y)

                    if (speed > minVelocity) {
                        gs.fling(
                            velocity = Velocity(velocityX, velocityY),
                            onDelta = { delta ->
                                controller.onGestureUpdate(
                                    1f,
                                    delta,
                                    Offset.Zero
                                )
                            },
                            onEnd = { controller.onGestureEnd() }
                        )
                    } else {
                        controller.onGestureEnd()
                    }
                }
            } else {
                // It was a tap (no movement > slop)
                // Check for double tap

                val now = System.currentTimeMillis()
                val elapsed = now - firstDownTime

                // If tap was too long, it's not a double-tap candidate (it was a long press or hold)
                if (elapsed > 300) {
                    controller.onGestureEnd()
                } else {
                    // Wait for second tap
                    val remainingTime = doubleTapTimeout - elapsed

                    // Try to catch the second down event
                    var secondDown: androidx.compose.ui.input.pointer.PointerInputChange? = null

                    try {
                        secondDown = withTimeoutOrNull(remainingTime) {
                            awaitFirstDown(requireUnconsumed = false)
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }

                    if (secondDown != null) {
                        // Second tap detected!
                        val dist = (secondDown.position - firstDownPos).getDistance()

                        if (dist <= doubleTapRadius) {
                            // Valid double tap
                            // Check if over page
                            if (controller.isPointOverPage(secondDown.position)) {
                                val targetZoom = if (state.zoom < config.doubleTapZoom * 0.9f) {
                                    config.doubleTapZoom
                                } else {
                                    config.minZoom
                                }

                                gs.animateZoom(
                                    from = state.zoom,
                                    to = targetZoom,
                                    pivot = secondDown.position,
                                    onFrame = { z, p -> controller.onAnimatedZoomFrame(z, p) },
                                    onEnd = { controller.onGestureEnd() }
                                )

                                secondDown.consume() // Consume to prevent re-trigger
                            } else {
                                controller.onGestureEnd()
                            }
                        } else {
                            // Too far away, treat as new gesture or ignore
                            controller.onGestureEnd()
                        }
                    } else {
                        // Timeout - Single tap confirmed
                        controller.onGestureEnd()
                    }
                }
            }
        }
    }
}

