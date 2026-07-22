/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.ui.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.composepdf.PdfGestureSpec
import com.composepdf.PdfTapEvent
import com.composepdf.PdfViewerState
import com.composepdf.PdfZoomSpec
import com.composepdf.internal.logic.ViewerController
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The viewer's complete touch interaction: pan, fling, pinch-to-zoom, double-tap zoom, quick scale
 * (double tap + drag), taps and long presses — one state machine, so gestures hand over to each
 * other without dead frames and any ongoing animation is interrupted by the next touch.
 *
 * Pan deltas participate in nested scrolling and drive the platform stretch [OverscrollEffect];
 * zoom past the configured limits is allowed with logarithmic resistance and springs back on
 * release.
 */
@Composable
internal fun Modifier.pdfViewerGestures(
    controller: ViewerController,
    state: PdfViewerState,
    gestureSpec: PdfGestureSpec,
    zoomSpec: PdfZoomSpec,
    overscrollEffect: OverscrollEffect?,
    onTap: ((PdfTapEvent) -> Unit)?,
    onLongPress: ((PdfTapEvent) -> Unit)?,
    enabled: Boolean,
): Modifier {
    val dispatcher = remember { NestedScrollDispatcher() }
    val connection = remember { object : NestedScrollConnection {} }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)

    return this.nestedScroll(connection, dispatcher).pointerInput(
        controller,
        gestureSpec,
        zoomSpec,
        overscrollEffect,
        enabled,
    ) {
        if (!enabled) return@pointerInput
        val session =
            GestureSession(
                controller = controller,
                state = state,
                gestureSpec = gestureSpec,
                zoomSpec = zoomSpec,
                overscrollEffect = overscrollEffect,
                dispatcher = dispatcher,
                decaySpec = splineBasedDecay(this),
                touchSlop = viewConfiguration.touchSlop,
                longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis,
                doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis,
                maxFlingVelocity = viewConfiguration.maximumFlingVelocity,
                minFlingVelocity = viewConfiguration.minimumFlingVelocity,
                quickScaleDoubleDistance = QUICK_SCALE_DOUBLE_DISTANCE.toPx(),
                onTap = { event -> currentOnTap?.invoke(event) },
                onLongPress = { event -> currentOnLongPress?.invoke(event) },
                hasLongPressListener = { currentOnLongPress != null },
            )
        awaitEachGesture { with(session) { handleGesture() } }
    }
}

/** Vertical quick-scale drag distance that doubles (or halves) the zoom. */
private val QUICK_SCALE_DOUBLE_DISTANCE = 220.dp

private enum class GestureMode {
    UNDECIDED,
    PAN,
    TRANSFORM,
}

private class GestureSession(
    private val controller: ViewerController,
    private val state: PdfViewerState,
    private val gestureSpec: PdfGestureSpec,
    private val zoomSpec: PdfZoomSpec,
    private val overscrollEffect: OverscrollEffect?,
    private val dispatcher: NestedScrollDispatcher,
    private val decaySpec: DecayAnimationSpec<Offset>,
    private val touchSlop: Float,
    private val longPressTimeoutMillis: Long,
    private val doubleTapTimeoutMillis: Long,
    private val maxFlingVelocity: Float,
    private val minFlingVelocity: Float,
    private val quickScaleDoubleDistance: Float,
    private val onTap: (PdfTapEvent) -> Unit,
    private val onLongPress: (PdfTapEvent) -> Unit,
    private val hasLongPressListener: () -> Boolean,
) {
    private val velocityTracker = VelocityTracker()
    private var lastPivot = Offset.Zero

    suspend fun AwaitPointerEventScope.handleGesture() {
        val down = awaitFirstDown(requireUnconsumed = false)
        controller.stopAnimations()
        controller.setVelocity(Offset.Zero)
        velocityTracker.resetTracking()
        velocityTracker.addPointerInputChange(down)

        var trackedId = down.id
        var mode = GestureMode.UNDECIDED
        var accumulatedPan = Offset.Zero
        var longPressFired = false
        var lastUptime = down.uptimeMillis
        var tapCandidate = false

        while (true) {
            val event =
                if (mode == GestureMode.UNDECIDED && !longPressFired && hasLongPressListener()) {
                    val remaining = longPressTimeoutMillis - (lastUptime - down.uptimeMillis)
                    if (remaining <= 0) null
                    else withTimeoutOrNull(remaining) { awaitPointerEvent() }
                } else {
                    awaitPointerEvent()
                }

            if (event == null) {
                longPressFired = true
                onLongPress(controller.tapEventAt(down.position))
                continue
            }
            lastUptime = event.changes.first().uptimeMillis

            trackedId = trackVelocity(event, trackedId)

            when (mode) {
                GestureMode.UNDECIDED -> {
                    if (event.pressedCount() > 1 && gestureSpec.isZoomEnabled) {
                        mode = GestureMode.TRANSFORM
                        controller.onGestureStart()
                    } else {
                        accumulatedPan += event.calculatePan()
                        if (accumulatedPan.getDistanceSquared() > touchSlop * touchSlop) {
                            mode = GestureMode.PAN
                            controller.onGestureStart()
                            scrollBy(accumulatedPan, NestedScrollSource.UserInput)
                            event.consumePositionChanges()
                        }
                    }
                }

                GestureMode.PAN -> {
                    if (event.pressedCount() > 1 && gestureSpec.isZoomEnabled) {
                        mode = GestureMode.TRANSFORM
                    }
                    val pan = event.calculatePan()
                    if (pan != Offset.Zero) scrollBy(pan, NestedScrollSource.UserInput)
                    event.consumePositionChanges()
                }

                GestureMode.TRANSFORM -> {
                    val zoomChange = event.calculateZoom()
                    val pan = event.calculatePan()
                    val centroid = event.calculateCentroid(useCurrent = false)
                    if (centroid != Offset.Unspecified) lastPivot = centroid
                    if (zoomChange != 1f) {
                        controller.zoomTo(rubberBandZoom(state.zoom * zoomChange), lastPivot)
                    }
                    if (pan != Offset.Zero) scrollBy(pan, NestedScrollSource.UserInput)
                    event.consumePositionChanges()
                }
            }

            if (event.changes.all { !it.pressed }) {
                tapCandidate = mode == GestureMode.UNDECIDED && !longPressFired
                break
            }
        }

        when {
            mode != GestureMode.UNDECIDED -> {
                val velocity = clampedVelocity()
                controller.onGestureEnd()
                settleOrFling(velocity)
            }

            tapCandidate -> handleTapOrDoubleTap(down.position)

            else -> controller.setVelocity(Offset.Zero)
        }
    }

    // ------------------------------------------------------------------ taps

    private suspend fun AwaitPointerEventScope.handleTapOrDoubleTap(firstTapPosition: Offset) {
        controller.setVelocity(Offset.Zero)
        val doubleTapCapable =
            gestureSpec.isZoomEnabled &&
                (gestureSpec.isDoubleTapZoomEnabled || gestureSpec.isQuickScaleEnabled)
        if (!doubleTapCapable) {
            onTap(controller.tapEventAt(firstTapPosition))
            return
        }

        val secondDown =
            withTimeoutOrNull(doubleTapTimeoutMillis) { awaitFirstDown(requireUnconsumed = false) }
        if (secondDown == null) {
            onTap(controller.tapEventAt(firstTapPosition))
            return
        }

        controller.stopAnimations()
        secondDown.consume()

        var quickScaling = false
        val startZoom = state.zoom
        val pivot = secondDown.position
        velocityTracker.resetTracking()
        velocityTracker.addPointerInputChange(secondDown)

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == secondDown.id }

            if (change != null && change.pressed) {
                val travel = change.position - secondDown.position
                if (
                    !quickScaling &&
                        gestureSpec.isQuickScaleEnabled &&
                        travel.getDistanceSquared() > touchSlop * touchSlop
                ) {
                    quickScaling = true
                    controller.onGestureStart()
                }
                if (quickScaling) {
                    val target =
                        startZoom * 2f.pow((change.position.y - pivot.y) / quickScaleDoubleDistance)
                    controller.zoomTo(rubberBandZoom(target), pivot)
                    event.consumePositionChanges()
                }
            }

            if (event.changes.all { !it.pressed }) {
                if (quickScaling) {
                    controller.onGestureEnd()
                    settleOrFling(Velocity.Zero)
                } else if (gestureSpec.isDoubleTapZoomEnabled) {
                    launchDoubleTapZoom(pivot)
                }
                return
            }
        }
    }

    private fun launchDoubleTapZoom(pivot: Offset) {
        val fitZoom = controller.fitPageZoom(state.currentPage)
        val expandedZoom =
            zoomSpec.doubleTapZoom
                .coerceIn(zoomSpec.minZoom, zoomSpec.maxZoom)
                .coerceAtLeast(fitZoom * 1.25f)
        // Geometric midpoint: below it a double tap zooms in, above it a double tap restores fit.
        val target = if (state.zoom < sqrt(fitZoom * expandedZoom)) expandedZoom else fitZoom

        controller.launchTransform {
            Animatable(state.zoom).animateTo(target, DoubleTapZoomSpec) {
                controller.zoomTo(value, pivot)
            }
            controller.requestPlan()
        }
    }

    // ------------------------------------------------------------------ release

    /** Settles an out-of-bounds zoom, snaps to a page, or flings — whichever applies. */
    private fun settleOrFling(velocity: Velocity) {
        val zoom = state.zoom
        val overZoomed = zoom < zoomSpec.minZoom * 0.999f || zoom > zoomSpec.maxZoom * 1.001f

        when {
            overZoomed -> {
                val target = zoom.coerceIn(zoomSpec.minZoom, zoomSpec.maxZoom)
                val pivot =
                    if (lastPivot != Offset.Zero) lastPivot
                    else Offset(controller.viewportWidth / 2f, controller.viewportHeight / 2f)
                controller.launchTransform {
                    Animatable(zoom).animateTo(target, SettleSpec) {
                        controller.zoomTo(value, pivot)
                    }
                    controller.setVelocity(Offset.Zero)
                    controller.requestPlan()
                }
            }

            else -> {
                val snapPage = controller.snapTargetPage(Offset(velocity.x, velocity.y))
                if (snapPage != null) {
                    controller.launchTransform {
                        val pan = controller.centeredPanForPage(snapPage)
                        animatePanRaw(pan.x, pan.y)
                        controller.setVelocity(Offset.Zero)
                        controller.requestPlan()
                    }
                } else if (gestureSpec.isFlingEnabled && velocity.magnitude() > minFlingVelocity) {
                    fling(velocity)
                } else {
                    controller.setVelocity(Offset.Zero)
                }
            }
        }
    }

    private fun fling(initialVelocity: Velocity) {
        controller.launchTransform {
            val performFling: suspend (Velocity) -> Velocity = { available ->
                val preConsumed = dispatcher.dispatchPreFling(available)
                val toFling = available - preConsumed
                var remaining = Velocity.Zero

                if (abs(toFling.x) > 1f || abs(toFling.y) > 1f) {
                    var lastValue = Offset.Zero
                    AnimationState(
                            typeConverter = Offset.VectorConverter,
                            initialValue = Offset.Zero,
                            initialVelocityVector = AnimationVector2D(toFling.x, toFling.y),
                        )
                        .animateDecay(decaySpec) {
                            val delta = value - lastValue
                            lastValue = value
                            val consumed = scrollBy(delta, NestedScrollSource.SideEffect)
                            controller.setVelocity(velocity)
                            val unconsumed = delta - consumed
                            val blocked =
                                consumed.getDistanceSquared() < 0.01f &&
                                    unconsumed.getDistanceSquared() > 0.25f
                            if (blocked) {
                                remaining = Velocity(velocity.x, velocity.y)
                                cancelAnimation()
                            }
                        }
                }

                val consumedVelocity = available - remaining
                dispatcher.dispatchPostFling(consumedVelocity, remaining)
                consumedVelocity
            }

            try {
                overscrollEffect?.applyToFling(initialVelocity, performFling)
                    ?: performFling(initialVelocity)
            } finally {
                controller.setVelocity(Offset.Zero)
                controller.requestPlan()
            }
        }
    }

    // ------------------------------------------------------------------ plumbing

    /**
     * Routes a pan delta through nested scrolling and the overscroll effect. Returns the portion
     * consumed by the viewer itself.
     */
    private fun scrollBy(delta: Offset, source: NestedScrollSource): Offset {
        val performScroll: (Offset) -> Offset = { d ->
            val preConsumed = dispatcher.dispatchPreScroll(d, source)
            val consumed = controller.panBy(d - preConsumed)
            dispatcher.dispatchPostScroll(consumed, d - preConsumed - consumed, source)
            preConsumed + consumed
        }
        return if (overscrollEffect != null) {
            overscrollEffect.applyToScroll(delta, source, performScroll)
        } else {
            performScroll(delta)
        }
    }

    /** Applies logarithmic resistance to zoom outside the committed limits. */
    private fun rubberBandZoom(raw: Float): Float {
        if (raw <= 0f) return zoomSpec.minZoom
        if (!zoomSpec.overZoom) return raw.coerceIn(zoomSpec.minZoom, zoomSpec.maxZoom)
        return when {
            raw > zoomSpec.maxZoom ->
                exp(ln(zoomSpec.maxZoom) + (ln(raw) - ln(zoomSpec.maxZoom)) * OVER_ZOOM_RESISTANCE)

            raw < zoomSpec.minZoom ->
                exp(ln(zoomSpec.minZoom) - (ln(zoomSpec.minZoom) - ln(raw)) * OVER_ZOOM_RESISTANCE)

            else -> raw
        }
    }

    private suspend fun animatePanRaw(targetX: Float, targetY: Float) {
        val startX = state.panX
        val startY = state.panY
        Animatable(0f).animateTo(1f, SettleSpec) {
            val x = startX + (targetX - startX) * value
            val y = startY + (targetY - startY) * value
            controller.panBy(Offset(x - state.panX, y - state.panY))
        }
    }

    /** Keeps the velocity tracker attached to a live pointer and publishes velocity. */
    private fun trackVelocity(event: PointerEvent, trackedId: PointerId): PointerId {
        var id = trackedId
        if (event.changes.none { it.id == id && it.pressed }) {
            val replacement = event.changes.firstOrNull { it.pressed } ?: return id
            id = replacement.id
            velocityTracker.resetTracking()
        }
        val tracked = event.changes.firstOrNull { it.id == id }
        if (tracked != null && tracked.positionChanged()) {
            velocityTracker.addPointerInputChange(tracked)
            val v = velocityTracker.calculateVelocity()
            controller.setVelocity(Offset(v.x, v.y))
        }
        return id
    }

    private fun clampedVelocity(): Velocity {
        val raw = velocityTracker.calculateVelocity()
        return Velocity(
            raw.x.coerceIn(-maxFlingVelocity, maxFlingVelocity),
            raw.y.coerceIn(-maxFlingVelocity, maxFlingVelocity),
        )
    }

    private fun PointerEvent.pressedCount(): Int = changes.count { it.pressed }

    private fun PointerEvent.consumePositionChanges() {
        for (i in changes.indices) {
            val change = changes[i]
            if (change.positionChanged()) change.consume()
        }
    }

    private fun Velocity.magnitude(): Float = sqrt(x * x + y * y)

    companion object {
        /** How much of the pinch past a zoom limit is actually applied (log space). */
        const val OVER_ZOOM_RESISTANCE = 0.35f

        private val DoubleTapZoomSpec =
            spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

        private val SettleSpec =
            spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f)
    }
}
