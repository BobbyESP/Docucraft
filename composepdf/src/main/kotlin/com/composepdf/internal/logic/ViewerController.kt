/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.logic

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.ui.geometry.Offset
import com.composepdf.PdfTapEvent
import kotlinx.coroutines.Job

/**
 * The single contract between [PdfViewerController] and everything that talks to it: the hoisted
 * [com.composepdf.PdfViewerState], the document canvas, the overlays and the gesture layer.
 *
 * Transform semantics: [panBy] and [zoomTo] are raw frame operations — [panBy] clamps to content
 * bounds and reports what it consumed (feeding nested scroll and overscroll), while [zoomTo]
 * accepts values outside the configured limits so the gesture layer can rubber-band. Committed
 * limits are enforced by the callers that own the gesture or animation.
 */
internal interface ViewerController {
    val config: ResolvedViewerConfig
    val viewportWidth: Float
    val viewportHeight: Float

    /** Immutable geometry of the current document layout at zoom `1f`. */
    fun layout(): PageLayoutSnapshot

    fun visiblePageIndices(): IntRange

    // ------------------------------------------------------------------ frame transforms

    /** Pans by [delta], clamped to content bounds. Returns the consumed portion. */
    fun panBy(delta: Offset): Offset

    /** Sets the absolute [zoom] keeping [pivot] stationary on screen. Does not clamp [zoom]. */
    fun zoomTo(zoom: Float, pivot: Offset)

    /** Latest pointer/fling velocity, feeding the engine's predictive prefetch. */
    fun setVelocity(velocity: Offset)

    // ------------------------------------------------------------------ gesture lifecycle

    fun onGestureStart()

    fun onGestureEnd()

    // ------------------------------------------------------------------ queries

    fun tapEventAt(position: Offset): PdfTapEvent

    fun fitPageZoom(pageIndex: Int): Float

    fun fitDocumentZoom(): Float

    fun centeredPanForPage(pageIndex: Int): PanPosition

    /**
     * The page a released scroll should settle on when page snapping applies, or `null` when
     * snapping is disabled or the zoom level makes it inappropriate.
     */
    fun snapTargetPage(velocity: Offset): Int?

    // ------------------------------------------------------------------ animations

    /**
     * Runs [block] holding the shared transform mutation slot: starting another animation or a
     * touch gesture cancels it. All programmatic and gesture-driven animations go through here.
     */
    suspend fun transform(block: suspend () -> Unit)

    /** Launches [transform] in the controller's scope. For fire-and-forget gesture animations. */
    fun launchTransform(block: suspend () -> Unit): Job

    /** Cancels whatever animation currently holds the transform slot. */
    fun stopAnimations()

    suspend fun animateZoomTo(
        targetZoom: Float,
        pivot: Offset?,
        animationSpec: AnimationSpec<Float>,
    )

    suspend fun animatePanTo(targetX: Float, targetY: Float, animationSpec: AnimationSpec<Float>)

    // ------------------------------------------------------------------ environment

    fun onViewportSizeChanged(width: Float, height: Float)

    fun updateConfig(newConfig: ResolvedViewerConfig)

    fun requestPlan()
}
