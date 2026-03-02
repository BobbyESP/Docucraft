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
    private var animJob: Job?  = null

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

// ── pdfGestures modifier ──────────────────────────────────────────────────────

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
 * or zoom (converted to pixels via viewport width) exceeds [ViewConfiguration.touchSlop].
 * This prevents micro-jitter from triggering scrolls during taps.
 *
 * ## Why [awaitEachGesture] instead of [detectTransformGestures]?
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

    return this.pointerInput(enabled) {
        if (!enabled) return@pointerInput

        val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
        val touchSlop        = viewConfiguration.touchSlop
        val doubleTapRadius  = touchSlop * 8f                 // generous for the second tap

        awaitEachGesture {

            // ── 1. Wait for first finger down ─────────────────────────────
            val firstDown     = awaitFirstDown(requireUnconsumed = false)
            val firstDownTime = System.currentTimeMillis()
            val firstDownPos  = firstDown.position

            gs.cancelAll()
            gs.reset()
            controller.onGestureStart()

            var zooming    = false
            var pastSlop   = false
            var multiTouch = false
            var accPan     = Offset.Zero
            var accZoom    = 1f

            // ── 2. Main gesture loop ──────────────────────────────────────
            var event: PointerEvent
            do {
                event = awaitPointerEvent()
                val nPressed = event.changes.count { it.pressed }
                if (nPressed > 1) { multiTouch = true; zooming = true }

                if (!event.changes.any { it.isConsumed }) {
                    val zoomDelta = event.calculateZoom()
                    val panDelta  = event.calculatePan()
                    val centroid  = event.calculateCentroid(useCurrent = false)

                    // Accumulate for slop detection
                    if (!pastSlop) {
                        accZoom *= zoomDelta
                        accPan  += panDelta
                        val panSq  = accPan.x * accPan.x + accPan.y * accPan.y
                        val zoomPx = kotlin.math.abs(1f - accZoom) * size.width
                        if (panSq > touchSlop * touchSlop || zoomPx > touchSlop) pastSlop = true
                    }

                    if (pastSlop) {
                        if (zoomDelta != 1f || panDelta != Offset.Zero) {
                            controller.onGestureUpdate(zoomDelta, panDelta, centroid)
                        }
                        // Track velocity for the primary pointer
                        event.changes.firstOrNull { it.pressed }?.let { ch ->
                            if (ch.positionChanged()) gs.velocityTracker.addPointerInputChange(ch)
                        }
                        event.changes.forEach { it.consume() }
                    }
                }

            } while (event.changes.any { it.pressed })

            // ── 3. Finger(s) lifted ───────────────────────────────────────
            if (pastSlop) {
                val vel       = gs.velocityTracker.calculateVelocity()
                val speedSq   = vel.x * vel.x + vel.y * vel.y
                val threshold = 1000f * 1000f     // 1000 px/s

                if (speedSq > threshold) {
                    gs.fling(
                        velocity = vel,
                        onDelta  = { delta ->
                            controller.onGestureUpdate(1f, delta, Offset.Zero)
                        },
                        onEnd    = { controller.onGestureEnd() }
                    )
                } else {
                    controller.onGestureEnd()
                }
                return@awaitEachGesture
            }

            // ── 4. Not a drag — check for double-tap ──────────────────────
            if (multiTouch) {
                controller.onGestureEnd()
                return@awaitEachGesture
            }

            val elapsed   = System.currentTimeMillis() - firstDownTime
            val remaining = (doubleTapTimeout - elapsed).coerceAtLeast(0L)

            // awaitFirstDown correctly waits for the PRESS event of the second tap.
            // The previous manual loop broke because it saw the RELEASE of the first
            // tap and interpreted it as "no second tap found".
            val secondDown = withTimeoutOrNull(remaining) {
                awaitFirstDown(requireUnconsumed = false)
            }

            if (secondDown == null) {
                controller.onGestureEnd()
                return@awaitEachGesture
            }

            // Verify the second tap is close enough to count as a double-tap
            if ((secondDown.position - firstDownPos).getDistance() > doubleTapRadius) {
                controller.onGestureEnd()
                return@awaitEachGesture
            }

            val pivot = secondDown.position

            // Double-tap on grey background — no zoom
            if (!controller.isPointOverPage(pivot)) {
                controller.onGestureEnd()
                return@awaitEachGesture
            }

            secondDown.consume()

            val targetZoom = if (state.zoom < config.doubleTapZoom * 0.9f) {
                config.doubleTapZoom
            } else {
                config.minZoom
            }

            gs.animateZoom(
                from    = state.zoom,
                to      = targetZoom,
                pivot   = pivot,
                onFrame = { z, p -> controller.onAnimatedZoomFrame(z, p) },
                onEnd   = { controller.onGestureEnd() }
            )
        }
    }
}

