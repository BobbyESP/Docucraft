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
import com.composepdf.state.PdfViewerState
import com.composepdf.state.ViewerConfig
import com.composepdf.state.ViewerGestureController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Manages the state and execution of coroutine-based animations for document interaction.
 *
 * This class handles the lifecycle of fling (inertia) and zoom animations, ensuring that
 * concurrent animations are properly cancelled to prevent race conditions. It also maintains
 * a [VelocityTracker] to provide smooth transitions between manual gestures and automated flings.
 *
 * @property scope The [CoroutineScope] in which animation jobs are launched.
 */
@Stable
internal class GestureState(private val scope: CoroutineScope) {

    val velocityTracker = VelocityTracker()
    private var flingJob: Job? = null
    private var animJob: Job? = null

    /** Resets velocity tracking at the start of each new gesture. */
    fun reset() = velocityTracker.resetTracking()

    /** Cancels any in-progress fling or zoom animation. */
    fun cancelAll() {
        flingJob?.cancel()
        animJob?.cancel()
    }

    /**
     * Runs an exponential-decay fling animation on both the X and Y axes.
     *
     * As the animation progresses, it invokes [onDelta] to apply scroll offsets and
     * [onVelocityUpdate] to provide an approximation of the current velocity to the
     * render pipeline for tile optimization.
     *
     * @param velocity The initial velocity of the fling.
     * @param onDelta Callback invoked with the incremental offset change for each frame.
     * @param onVelocityUpdate Callback invoked to update the current scroll velocity.
     * @param onEnd Callback invoked when the fling animation finishes or is canceled.
     */
    fun fling(
        velocity: Velocity,
        onDelta: (Offset) -> Unit,
        onVelocityUpdate: (Offset) -> Unit,
        onEnd: () -> Unit
    ) {
        flingJob?.cancel()
        flingJob = scope.launch {
            val decay = exponentialDecay<Float>(frictionMultiplier = 1.15f)
            val jx = launch {
                var last = 0f
                Animatable(0f).animateDecay(velocity.x, decay) {
                    onDelta(Offset(value - last, 0f))
                    // Approximation of current velocity for the render pipeline
                    onVelocityUpdate(Offset(velocity.x, velocity.y))
                    last = value
                }
            }
            val jy = launch {
                var last = 0f
                Animatable(0f).animateDecay(velocity.y, decay) {
                    onDelta(Offset(0f, value - last))
                    last = value
                }
            }
            jx.join(); jy.join()
            onVelocityUpdate(Offset.Zero)
            onEnd()
        }
    }

    /**
     * Runs a spring-interpolated zoom animation.
     *
     * @param from The starting zoom level.
     * @param to The target zoom level to animate towards.
     * @param pivot The screen coordinate around which the zoom should be centered.
     * @param onFrame Callback invoked on every animation frame with the current zoom and pivot.
     * @param onEnd Callback invoked when the animation completes or is fully applied.
     * @param spec The [AnimationSpec] defining the spring dynamics of the transition.
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

/**
 * Creates and remembers a [GestureState] instance across recompositions.
 *
 * This function provides a [GestureState] tied to the composition's [CoroutineScope],
 * allowing for gesture-driven animations (like flings and zooms) to be managed and
 * automatically cancelled when the composable leaves the composition.
 *
 * @return A remembered [GestureState] instance.
 */
@Composable
internal fun rememberGestureState(): GestureState {
    val scope = rememberCoroutineScope()
    return remember { GestureState(scope) }
}

/**
 * A unified gesture [Modifier] for PDF interaction that handles pinch-to-zoom, panning,
 * momentum-based flinging, and multi-stage double-tap zooming.
 *
 * This modifier coordinates touch events with the [ViewerGestureController] to update
 * the [PdfViewerState]. It includes built-in touch slop detection to prevent accidental
 * jitter and provides continuous velocity tracking to the state to assist in rendering
 * optimizations (e.g., low-resolution tiling during fast movement).
 *
 * Double-tap logic cycles through zoom levels:
 * 1. From fit-to-screen to [ViewerConfig.doubleTapZoom].
 * 2. From [ViewerConfig.doubleTapZoom] to [ViewerConfig.maxZoom].
 * 3. From [ViewerConfig.maxZoom] back to fit-to-screen.
 *
 * @param state The current state of the PDF viewer, updated during pan, zoom, and fling.
 * @param controller The gesture controller that translates raw deltas into coordinate transforms.
 * @param config Configuration for zoom limits and double-tap behavior.
 * @param zoomAnimationSpec The animation curve used for animated double-tap zoom transitions.
 * @param enabled Whether gesture processing is active.
 */
@Composable
internal fun Modifier.viewerGestures(
    state: PdfViewerState,
    controller: ViewerGestureController,
    config: ViewerConfig,
    zoomAnimationSpec: AnimationSpec<Float> = spring(dampingRatio = 0.72f, stiffness = 420f),
    enabled: Boolean = true
): Modifier {
    val gs = rememberGestureState()
    val viewConfiguration = androidx.compose.ui.platform.LocalViewConfiguration.current

    return this.pointerInput(enabled) {
        if (!enabled) return@pointerInput

        val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
        val touchSlop = viewConfiguration.touchSlop
        val doubleTapRadius = 100f

        awaitEachGesture {
            // Finger down - reset state and stop animations
            val firstDown = awaitFirstDown(requireUnconsumed = false)
            val firstDownTime = System.currentTimeMillis()
            val firstDownPos = firstDown.position

            gs.cancelAll()
            gs.reset()
            state.scrollVelocity = Offset.Zero
            controller.onGestureStart()

            var zooming = false
            var pastSlop = false
            var accPan = Offset.Zero
            var accZoom = 1f

            var velocityTrackerId = firstDown.id
            gs.velocityTracker.addPointerInputChange(firstDown)

            var event: PointerEvent
            var canceled = false

            do {
                event = awaitPointerEvent()
                val changes = event.changes

                // Pointer switch handling
                if (changes.none { it.id == velocityTrackerId && it.pressed }) {
                    val newPrimary = changes.firstOrNull { it.pressed }
                    if (newPrimary != null) {
                        velocityTrackerId = newPrimary.id
                        gs.velocityTracker.resetTracking()
                    }
                }

                val pressedCount = changes.count { it.pressed }
                if (pressedCount > 1) zooming = true

                val zoomDelta = event.calculateZoom()
                val panDelta = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = false)

                // Accumulate movement until it exceeds touch slop
                if (!pastSlop) {
                    accZoom *= zoomDelta
                    accPan += panDelta

                    val panDistSq = accPan.getDistanceSquared()
                    val zoomDist = kotlin.math.abs(1f - accZoom)

                    if (panDistSq > touchSlop * touchSlop || zoomDist > 0.05f) {
                        pastSlop = true
                        controller.onGestureUpdate(accZoom, accPan, centroid)
                    }
                } else {
                    if (zoomDelta != 1f || panDelta != Offset.Zero) {
                        controller.onGestureUpdate(zoomDelta, panDelta, centroid)
                    }
                }

                // Continuous velocity tracking during drag
                val trackedChange = changes.firstOrNull { it.id == velocityTrackerId }
                if (trackedChange != null && trackedChange.positionChanged()) {
                    gs.velocityTracker.addPointerInputChange(trackedChange)
                    val v = gs.velocityTracker.calculateVelocity()
                    state.scrollVelocity = Offset(v.x, v.y)
                }

                if (pastSlop) {
                    changes.forEach { if (it.positionChanged()) it.consume() }
                }

                if (changes.all { !it.pressed }) canceled = true

            } while (!canceled && event.changes.any { it.pressed })

            // Handle end of gesture
            if (pastSlop) {
                if (zooming) {
                    state.scrollVelocity = Offset.Zero
                    controller.onGestureEnd()
                } else {
                    val velocity = gs.velocityTracker.calculateVelocity()
                    val maxVelocity = viewConfiguration.maximumFlingVelocity
                    val minVelocity = viewConfiguration.minimumFlingVelocity

                    val velocityX = velocity.x.coerceIn(-maxVelocity, maxVelocity)
                    val velocityY = velocity.y.coerceIn(-maxVelocity, maxVelocity)
                    val speed = kotlin.math.sqrt(velocityX * velocityX + velocityY * velocityY)

                    if (speed > minVelocity) {
                        gs.fling(
                            velocity = Velocity(velocityX, velocityY),
                            onDelta = { delta -> controller.onGestureUpdate(1f, delta, Offset.Zero) },
                            onVelocityUpdate = { state.scrollVelocity = it },
                            onEnd = { 
                                state.scrollVelocity = Offset.Zero
                                controller.onGestureEnd() 
                            }
                        )
                    } else {
                        state.scrollVelocity = Offset.Zero
                        controller.onGestureEnd()
                    }
                }
            } else {
                // Tap/Double-tap detection
                state.scrollVelocity = Offset.Zero
                val now = System.currentTimeMillis()
                val elapsed = now - firstDownTime

                if (elapsed > 300) {
                    controller.onGestureEnd()
                } else {
                    val remainingTime = doubleTapTimeout - elapsed
                    var secondDown: androidx.compose.ui.input.pointer.PointerInputChange? = null

                    try {
                        secondDown = withTimeoutOrNull(remainingTime) {
                            awaitFirstDown(requireUnconsumed = false)
                        }
                    } catch (_: Exception) {}

                    if (secondDown != null) {
                        val dist = (secondDown.position - firstDownPos).getDistance()
                        if (dist <= doubleTapRadius) {
                            if (controller.isPointOverPage(secondDown.position)) {
                                val fitZoom = controller.computeFitPageZoom(state.currentPage)
                                val tapZoom = config.doubleTapZoom
                                val maxZoom = config.maxZoom
                                val currentZoom = state.zoom

                                val atFit = currentZoom < tapZoom * 0.85f
                                val atTap = currentZoom in (tapZoom * 0.85f)..(maxZoom * 0.85f)

                                val targetZoom = when {
                                    atFit -> tapZoom
                                    atTap -> maxZoom
                                    else -> fitZoom
                                }

                                gs.animateZoom(
                                    from = state.zoom,
                                    to = targetZoom,
                                    pivot = secondDown.position,
                                    onFrame = { z, p -> controller.onAnimatedZoomFrame(z, p) },
                                    onEnd = { controller.onGestureEnd() },
                                    spec = zoomAnimationSpec
                                )
                                secondDown.consume()
                            } else {
                                controller.onGestureEnd()
                            }
                        } else {
                            controller.onGestureEnd()
                        }
                    } else {
                        controller.onGestureEnd()
                    }
                }
            }
        }
    }
}
