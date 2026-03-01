package com.composepdf.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlinx.coroutines.launch

/**
 * State holder for gesture animations and tracking.
 */
@Stable
internal class GestureState(
    private val scope: CoroutineScope
) {
    val offsetAnimatable = Animatable(Offset.Zero, Offset.VectorConverter)
    val velocityTracker = VelocityTracker()
    
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
    
    fun animateZoomTo(
        targetZoom: Float,
        currentZoom: Float,
        pivot: Offset,
        onUpdate: (Float, Offset) -> Unit,
        animationSpec: AnimationSpec<Float> = spring()
    ) {
        scope.launch {
            Animatable(currentZoom).animateTo(targetZoom, animationSpec) {
                onUpdate(value, pivot)
            }
        }
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
 * This modifier combines:
 * - Pinch-to-zoom with centroid tracking
 * - Pan when zoomed
 * - Double-tap to toggle zoom
 * - Fling with velocity-based animation
 * 
 * @param state The PDF viewer state
 * @param controller The PDF viewer controller
 * @param config The viewer configuration
 * @param enabled Whether gestures are enabled
 */
@Composable
fun Modifier.pdfGestures(
    state: PdfViewerState,
    controller: PdfViewerController,
    config: ViewerConfig,
    enabled: Boolean = true
): Modifier {
    val gestureState = rememberGestureState()
    
    return this
        .pointerInput(enabled, config.isZoomGesturesEnabled) {
            if (!enabled || !config.isZoomGesturesEnabled) return@pointerInput
            
            awaitEachGesture {
                var zoom = 1f
                var pan = Offset.Zero
                var pastTouchSlop = false
                val touchSlop = viewConfiguration.touchSlop
                
                awaitFirstDown(requireUnconsumed = false)
                gestureState.resetVelocity()
                controller.onGestureStart()
                
                do {
                    val event = awaitPointerEvent()
                    val canceled = event.changes.any { it.isConsumed }
                    
                    if (!canceled) {
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = false)
                        
                        if (!pastTouchSlop) {
                            zoom *= zoomChange
                            pan += panChange
                            
                            val centroidSize = (pan.x * pan.x + pan.y * pan.y)
                            val zoomMotion = kotlin.math.abs(1 - zoom) * size.width
                            
                            if (centroidSize > touchSlop * touchSlop || zoomMotion > touchSlop) {
                                pastTouchSlop = true
                            }
                        }
                        
                        if (pastTouchSlop) {
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                controller.onGestureUpdate(zoomChange, panChange, centroid)
                            }
                            
                            // Track velocity for fling
                            event.changes.firstOrNull()?.let { change ->
                                if (change.positionChanged()) {
                                    gestureState.trackVelocity(change.position)
                                }
                            }
                            
                            event.changes.forEach { it.consume() }
                        }
                    }
                } while (event.type != PointerEventType.Release && !canceled)
                
                // Handle fling if zoomed
                if (state.zoom > 1f && pastTouchSlop) {
                    val velocity = gestureState.velocityTracker.calculateVelocity()
                    if (velocity.x != 0f || velocity.y != 0f) {
                        gestureState.animateFling(
                            velocity = velocity,
                            onUpdate = { delta ->
                                controller.onGestureUpdate(1f, delta, Offset.Zero)
                            },
                            onEnd = {
                                controller.onGestureEnd()
                            }
                        )
                    } else {
                        controller.onGestureEnd()
                    }
                } else {
                    controller.onGestureEnd()
                }
            }
        }
        .pointerInput(enabled, config.isZoomGesturesEnabled) {
            if (!enabled || !config.isZoomGesturesEnabled) return@pointerInput
            
            detectTapGestures(
                onDoubleTap = { offset ->
                    controller.toggleDoubleTapZoom(offset)
                }
            )
        }
}
