/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.composepdf.internal.engine.BitmapPool
import com.composepdf.internal.logic.ViewerController
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * A hoistable state object that exposes the viewer's UI state and a programmatic navigation API.
 *
 * All rendering state (bitmaps, tiles, caches) lives inside the engine; this object only holds what
 * the UI and the host app need to observe and control.
 */
@Stable
class PdfViewerState(
    initialPage: Int = 0,
    initialZoom: Float = 1f,
    internal val bitmapPool: BitmapPool = BitmapPool(),
    internal val scope: CoroutineScope =
        CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
) {
    /** The index of the current page most visible in the viewport. */
    var currentPage: Int by mutableIntStateOf(initialPage)
        internal set

    /** Total number of pages in the current document. */
    var pageCount: Int by mutableIntStateOf(0)
        internal set

    /** Current magnification level. 1.0f means fit-to-width. */
    var zoom: Float by mutableFloatStateOf(initialZoom)
        internal set

    /** Horizontal translation offset in screen pixels. */
    var panX: Float by mutableFloatStateOf(0f)
        internal set

    /** Vertical translation offset in screen pixels. */
    var panY: Float by mutableFloatStateOf(0f)
        internal set

    /** Current scroll velocity in pixels per second. */
    var scrollVelocity: Offset by mutableStateOf(Offset.Zero)
        internal set

    /** Indicates if the document is currently being loaded. */
    var isLoading: Boolean by mutableStateOf(true)
        internal set

    /** Stores any error encountered during the PDF lifecycle. */
    var error: Throwable? by mutableStateOf(null)
        internal set

    /** True if a user gesture (pinch, pan) is currently active. */
    var isGestureActive: Boolean by mutableStateOf(false)
        internal set

    /** State of the remote document loading if applicable. */
    var remoteState: RemotePdfState by mutableStateOf(RemotePdfState.Idle)
        internal set

    /** True if a document is loaded and ready for interaction. */
    val isLoaded: Boolean
        get() = !isLoading && error == null && pageCount > 0

    internal var controller: ViewerController? = null

    val minZoom: Float
        get() = controller?.viewerConfig?.minZoom ?: 1f

    val maxZoom: Float
        get() = controller?.viewerConfig?.maxZoom ?: 5f

    // ------------------------------------------------------------------ document lifecycle

    internal fun beginDocumentLoad() {
        pageCount = 0
        isLoading = true
        error = null
        remoteState = RemotePdfState.Idle
    }

    internal fun updateRemoteDocumentState(state: RemotePdfState) {
        remoteState = state
    }

    internal fun completeDocumentLoad(pageCount: Int) {
        this.pageCount = pageCount
        isLoading = false
        error = null
    }

    internal fun failDocumentLoad(error: Throwable) {
        this.error = error
        isLoading = false
    }

    internal fun reset() {
        currentPage = 0
        zoom = 1f
        panX = 0f
        panY = 0f
        scrollVelocity = Offset.Zero
        isGestureActive = false
        beginDocumentLoad()
    }

    // ------------------------------------------------------------------ programmatic API

    /** Instantly jumps to [pageIndex] without animation. */
    fun scrollToPage(pageIndex: Int) {
        val ctrl = controller ?: return
        val target = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val (targetPanX, targetPanY) = ctrl.computeCenteredPanForPage(target)

        panX = targetPanX
        panY = targetPanY
        currentPage = target
        ctrl.clampPan()
        ctrl.requestRenderForVisiblePages()
    }

    /** Smoothly animates the scroll to [pageIndex]. */
    suspend fun animateScrollToPage(
        pageIndex: Int,
        animationSpec: AnimationSpec<Float> = spring(),
    ) {
        val ctrl = controller ?: return
        val target = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val (targetPanX, targetPanY) = ctrl.computeCenteredPanForPage(target)

        val startPanX = panX
        val startPanY = panY

        currentPage = target
        Animatable(0f).animateTo(targetValue = 1f, animationSpec = animationSpec) {
            panX = startPanX + (targetPanX - startPanX) * value
            panY = startPanY + (targetPanY - startPanY) * value
            ctrl.clampPan()
            ctrl.requestRenderForVisiblePages()
        }
        currentPage = target
    }

    /** Instantly sets the zoom level centered on the viewport center. */
    fun setZoom(zoomLevel: Float) {
        val ctrl = controller ?: return
        val pivot = Offset(ctrl.viewportWidth / 2f, ctrl.viewportHeight / 2f)
        ctrl.onAnimatedZoomFrame(zoomLevel, pivot)
    }

    /** Smoothly animates to the given absolute zoom level, centered on the viewport. */
    suspend fun animateZoomTo(zoomLevel: Float, animationSpec: AnimationSpec<Float> = spring()) {
        val ctrl = controller ?: return
        val pivot = Offset(ctrl.viewportWidth / 2f, ctrl.viewportHeight / 2f)
        val clampedTarget = zoomLevel.coerceIn(ctrl.viewerConfig.minZoom, ctrl.viewerConfig.maxZoom)

        Animatable(zoom).animateTo(targetValue = clampedTarget, animationSpec = animationSpec) {
            ctrl.onAnimatedZoomFrame(value, pivot)
        }
    }

    /** Zooms in by [factor] relative to the current zoom, centered on the viewport. */
    fun zoomIn(factor: Float = 0.25f) {
        setZoom(zoom * (1f + factor))
    }

    /** Zooms out by [factor] relative to the current zoom, centered on the viewport. */
    fun zoomOut(factor: Float = 0.25f) {
        setZoom(zoom * (1f - factor))
    }

    /** Resets the zoom to fit the current page in the viewport. */
    suspend fun animateResetZoom(animationSpec: AnimationSpec<Float> = spring()) {
        val ctrl = controller ?: return
        val targetZoom = ctrl.computeFitPageZoom(currentPage)
        val alreadyFit = abs(zoom - targetZoom) / targetZoom < 0.02f

        if (alreadyFit) {
            val (targetPanX, targetPanY) = ctrl.computeCenteredPanForPage(currentPage)
            val needsX = abs(panX - targetPanX) > 1f
            val needsY = abs(panY - targetPanY) > 1f
            if (!needsX && !needsY) return

            val startPanX = panX
            val startPanY = panY

            Animatable(0f).animateTo(1f, animationSpec) {
                if (needsX) panX = startPanX + (targetPanX - startPanX) * value
                if (needsY) panY = startPanY + (targetPanY - startPanY) * value
                ctrl.clampPan()
                ctrl.requestRenderForVisiblePages()
            }
        } else {
            animateZoomTo(targetZoom, animationSpec)
        }
    }

    /** Changes the [FitMode] of the viewer at runtime. */
    fun setFitMode(fitMode: FitMode) {
        val ctrl = controller ?: return
        ctrl.updateConfig(ctrl.viewerConfig.copy(fitMode = fitMode))
    }

    /** Changes the [ScrollDirection] of the viewer at runtime. */
    fun setScrollDirection(direction: ScrollDirection) {
        val ctrl = controller ?: return
        ctrl.updateConfig(ctrl.viewerConfig.copy(scrollDirection = direction))
    }

    /** Enables or disables night mode (color inversion) at runtime. */
    fun setNightMode(enabled: Boolean) {
        val ctrl = controller ?: return
        ctrl.updateConfig(ctrl.viewerConfig.copy(isNightModeEnabled = enabled))
    }

    /** Enables or disables page snapping at runtime. */
    fun setPageSnapping(enabled: Boolean) {
        val ctrl = controller ?: return
        ctrl.updateConfig(ctrl.viewerConfig.copy(isPageSnappingEnabled = enabled))
    }

    companion object {
        /**
         * Creates a [Saver] for [PdfViewerState]. Note: the [scope] is not saved; a new one must be
         * provided upon restoration.
         */
        fun saver(bitmapPool: BitmapPool, scope: CoroutineScope): Saver<PdfViewerState, *> =
            listSaver(
                save = { listOf(it.currentPage, it.zoom, it.panX, it.panY) },
                restore = {
                    PdfViewerState(
                            initialPage = it[0] as Int,
                            initialZoom = it[1] as Float,
                            bitmapPool = bitmapPool,
                            scope = scope,
                        )
                        .also { s ->
                            s.panX = it[2] as Float
                            s.panY = it[3] as Float
                        }
                },
            )
    }
}
