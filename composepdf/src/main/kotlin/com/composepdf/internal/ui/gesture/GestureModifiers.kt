/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.ui.gesture

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
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Velocity
import com.composepdf.PdfViewerState
import com.composepdf.ViewerConfig
import com.composepdf.internal.logic.ViewerController
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Manages the state and execution of coroutine-based animations for document interaction. */
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
     * Runs an exponential-decay fling on both axes. [onVelocityUpdate] receives the decaying
     * velocity so the engine's predictive prefetch tracks the actual motion.
     */
    fun fling(
        velocity: Velocity,
        onDelta: (Offset) -> Unit,
        onVelocityUpdate: (Offset) -> Unit,
        onEnd: () -> Unit,
    ) {
        flingJob?.cancel()
        flingJob = scope.launch {
            val decay = exponentialDecay<Float>(frictionMultiplier = 1.15f)
            var currentVx = velocity.x
            var currentVy = velocity.y
            val jx = launch {
                var last = 0f
                Animatable(0f).animateDecay(velocity.x, decay) {
                    onDelta(Offset(value - last, 0f))
                    last = value
                    currentVx = this.velocity
                    onVelocityUpdate(Offset(currentVx, currentVy))
                }
                currentVx = 0f
            }
            val jy = launch {
                var last = 0f
                Animatable(0f).animateDecay(velocity.y, decay) {
                    onDelta(Offset(0f, value - last))
                    last = value
                    currentVy = this.velocity
                    onVelocityUpdate(Offset(currentVx, currentVy))
                }
                currentVy = 0f
            }
            jx.join()
            jy.join()
            onVelocityUpdate(Offset.Zero)
            onEnd()
        }
    }

    /** Runs a spring-interpolated zoom animation. */
    fun animateZoom(
        from: Float,
        to: Float,
        pivot: Offset,
        onFrame: (zoom: Float, pivot: Offset) -> Unit,
        onEnd: () -> Unit,
        spec: AnimationSpec<Float> = spring(dampingRatio = 0.72f, stiffness = 420f),
    ) {
        animJob?.cancel()
        animJob = scope.launch {
            Animatable(from).animateTo(to, spec) { onFrame(value, pivot) }
            onEnd()
        }
    }
}

/** Creates and remembers a [GestureState] instance across recompositions. */
@Composable
internal fun rememberGestureState(): GestureState {
    val scope = rememberCoroutineScope()
    return remember { GestureState(scope) }
}

/** A unified gesture [Modifier] for PDF interaction. */
@Composable
internal fun Modifier.viewerGestures(
    state: PdfViewerState,
    controller: ViewerController,
    config: ViewerConfig,
    zoomAnimationSpec: AnimationSpec<Float> = spring(dampingRatio = 0.72f, stiffness = 420f),
    enabled: Boolean = true,
): Modifier {
    val gs = rememberGestureState()
    val viewConfiguration = LocalViewConfiguration.current

    return this.pointerInput(enabled, config.isZoomGesturesEnabled) {
        if (!enabled) return@pointerInput

        val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
        val touchSlop = viewConfiguration.touchSlop
        val doubleTapRadius = 100f

        awaitEachGesture {
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

                if (changes.none { it.id == velocityTrackerId && it.pressed }) {
                    val newPrimary = changes.firstOrNull { it.pressed }
                    if (newPrimary != null) {
                        velocityTrackerId = newPrimary.id
                        gs.velocityTracker.resetTracking()
                    }
                }

                val pressedCount = changes.count { it.pressed }
                if (pressedCount > 1 && config.isZoomGesturesEnabled) zooming = true

                val zoomDelta = if (config.isZoomGesturesEnabled) event.calculateZoom() else 1f
                val panDelta = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = false)

                if (!pastSlop) {
                    accZoom *= zoomDelta
                    accPan += panDelta

                    val panDistSq = accPan.getDistanceSquared()
                    val zoomDist = abs(1f - accZoom)

                    if (
                        panDistSq > touchSlop * touchSlop ||
                            (config.isZoomGesturesEnabled && zoomDist > 0.05f)
                    ) {
                        pastSlop = true
                        controller.onGestureUpdate(accZoom, accPan, centroid)
                    }
                } else {
                    if (zoomDelta != 1f || panDelta != Offset.Zero) {
                        controller.onGestureUpdate(zoomDelta, panDelta, centroid)
                    }
                }

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
                    val speed = sqrt(velocityX * velocityX + velocityY * velocityY)

                    if (speed > minVelocity) {
                        gs.fling(
                            velocity = Velocity(velocityX, velocityY),
                            onDelta = { delta ->
                                controller.onGestureUpdate(1f, delta, Offset.Zero)
                            },
                            onVelocityUpdate = { state.scrollVelocity = it },
                            onEnd = {
                                state.scrollVelocity = Offset.Zero
                                controller.onGestureEnd()
                            },
                        )
                    } else {
                        state.scrollVelocity = Offset.Zero
                        controller.onGestureEnd()
                    }
                }
            } else {
                state.scrollVelocity = Offset.Zero
                val now = System.currentTimeMillis()
                val elapsed = now - firstDownTime

                if (elapsed > 300) {
                    controller.onGestureEnd()
                } else {
                    val remainingTime = doubleTapTimeout - elapsed
                    var secondDown: PointerInputChange? = null

                    try {
                        secondDown =
                            withTimeoutOrNull(remainingTime) {
                                awaitFirstDown(requireUnconsumed = false)
                            }
                    } catch (_: Exception) {}

                    if (secondDown != null && config.isZoomGesturesEnabled) {
                        val dist = (secondDown.position - firstDownPos).getDistance()
                        if (
                            dist <= doubleTapRadius &&
                                controller.isPointOverPage(secondDown.position)
                        ) {
                            val fitZoom = controller.computeFitPageZoom(state.currentPage)
                            val tapZoom = config.doubleTapZoom
                            val maxZoom = config.maxZoom
                            val currentZoom = state.zoom

                            val atFit = currentZoom < tapZoom * 0.85f
                            val atTap = currentZoom in (tapZoom * 0.85f)..(maxZoom * 0.85f)

                            val targetZoom =
                                when {
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
                                spec = zoomAnimationSpec,
                            )
                            secondDown.consume()
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
