/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.logic

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import com.composepdf.PdfSource
import com.composepdf.PdfTapEvent
import com.composepdf.PdfViewerState
import com.composepdf.ScrollDirection
import com.composepdf.internal.engine.PlanInputs
import com.composepdf.internal.engine.RenderEngine
import com.composepdf.internal.engine.TileDraw
import com.composepdf.internal.service.pdf.PdfDocumentManager
import com.composepdf.internal.service.pdf.PdfDocumentSession
import com.composepdf.internal.util.longLivedContext
import java.io.Closeable
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates the viewer: document lifecycle, transform state, viewport geometry and the render
 * engine. This is the only mutable hub; everything below it is either pure geometry
 * ([ViewerViewportCoordinator]/[PageLayoutSnapshot]) or the self-contained [RenderEngine].
 *
 * All animations — programmatic and gesture-driven — funnel through one [MutatorMutex], so a new
 * animation or an incoming touch always interrupts the previous motion cleanly.
 */
@Stable
internal class PdfViewerController(
    sourceContext: Context,
    val state: PdfViewerState,
    initialConfig: ResolvedViewerConfig = ResolvedViewerConfig(),
    val scope: CoroutineScope,
) : Closeable, ViewerController {

    val context: Context = sourceContext.longLivedContext()

    override var config by mutableStateOf(initialConfig)
        private set

    private val viewportCoordinator =
        ViewerViewportCoordinator(state = state, configProvider = { config })

    private val documentManager = PdfDocumentManager(context)
    private val documentSession = PdfDocumentSession(context, documentManager)

    private val engine =
        RenderEngine(
            documentManager = documentManager,
            pool = state.bitmapPool,
            inputsProvider = ::currentPlanInputs,
        )

    private val transformMutex = MutatorMutex()
    private var transformJob: Job? = null

    /** Base page bitmaps, observed by the UI. */
    val renderedPages: StateFlow<Map<Int, ImageBitmap>> = engine.baseBitmaps

    /** High-resolution tiles per page, observed by the UI. */
    val tiles: StateFlow<Map<Int, List<TileDraw>>> = engine.tiles

    private fun currentPlanInputs(): PlanInputs? {
        if (!viewportCoordinator.hasLayout) return null
        val currentConfig = config
        return PlanInputs(
            layout = viewportCoordinator.snapshot(),
            panX = state.panX,
            panY = state.panY,
            zoom = state.zoom,
            viewportWidth = viewportCoordinator.viewportWidth,
            viewportHeight = viewportCoordinator.viewportHeight,
            velocityX = state.scrollVelocity.x,
            velocityY = state.scrollVelocity.y,
            renderQuality = currentConfig.renderQuality,
            prefetchDistance = currentConfig.prefetchDistance,
        )
    }

    // ------------------------------------------------------------------ document lifecycle

    fun loadDocument(source: PdfSource) {
        scope.launch {
            state.reset()
            engine.invalidate()
            try {
                val document = documentSession.open(source, state::updateRemoteDocumentState)
                viewportCoordinator.updatePageSizes(document.pageSizes)
                state.completeDocumentLoad(document.pageCount)
                engine.requestPlan()
            } catch (error: Exception) {
                state.failDocumentLoad(error)
            }
        }
    }

    // ------------------------------------------------------------------ geometry

    override val viewportWidth: Float
        get() = viewportCoordinator.viewportWidth

    override val viewportHeight: Float
        get() = viewportCoordinator.viewportHeight

    override fun layout(): PageLayoutSnapshot = viewportCoordinator.snapshot()

    override fun visiblePageIndices(): IntRange = viewportCoordinator.visiblePageIndices()

    override fun fitPageZoom(pageIndex: Int): Float =
        viewportCoordinator.computeFitPageZoom(pageIndex)

    override fun fitDocumentZoom(): Float = viewportCoordinator.computeFitDocumentZoom()

    override fun centeredPanForPage(pageIndex: Int): PanPosition =
        viewportCoordinator.centeredPanForPage(pageIndex)

    override fun tapEventAt(position: Offset): PdfTapEvent {
        val snapshot = viewportCoordinator.snapshot()
        val page =
            snapshot.pageAtScreenPoint(position.x, position.y, state.panX, state.panY, state.zoom)
        if (page < 0) return PdfTapEvent(position, null, null)

        val zoom = state.zoom
        val left = snapshot.pageScreenLeft(page, state.panX, zoom)
        val top = snapshot.pageScreenTop(page, state.panY, zoom)
        val width = snapshot.pageWidthPx(page) * zoom
        val height = snapshot.pageHeightPx(page) * zoom
        if (width <= 0f || height <= 0f) return PdfTapEvent(position, page, null)

        val fraction =
            Offset(
                ((position.x - left) / width).coerceIn(0f, 1f),
                ((position.y - top) / height).coerceIn(0f, 1f),
            )
        return PdfTapEvent(position, page, fraction)
    }

    override fun snapTargetPage(velocity: Offset): Int? {
        val currentConfig = config
        if (!currentConfig.pageSnapping) return null
        val snapshot = viewportCoordinator.snapshot()
        if (snapshot.isEmpty) return null

        val zoom = state.zoom
        val current =
            snapshot.currentPageAtViewportCenter(state.panX, state.panY, zoom) ?: return null

        val vertical = currentConfig.scrollDirection == ScrollDirection.VERTICAL
        val pageSpan =
            (if (vertical) snapshot.pageHeightPx(current) else snapshot.pageWidthPx(current)) * zoom
        val viewportSpan = if (vertical) viewportHeight else viewportWidth
        // Snapping is a pager behaviour: it only applies while a whole page fits the viewport.
        if (pageSpan > viewportSpan * 1.05f) return null

        val axisVelocity = if (vertical) velocity.y else velocity.x
        val target =
            when {
                axisVelocity < -SNAP_FLING_VELOCITY -> current + 1
                axisVelocity > SNAP_FLING_VELOCITY -> current - 1
                else -> current
            }
        return target.coerceIn(0, snapshot.pageOffsets.lastIndex)
    }

    // ------------------------------------------------------------------ frame transforms

    override fun panBy(delta: Offset): Offset {
        if (delta == Offset.Zero) return Offset.Zero
        val snapshot = viewportCoordinator.snapshot()
        if (snapshot.isEmpty) return Offset.Zero

        val startX = state.panX
        val startY = state.panY
        val clamped = snapshot.clampPan(startX + delta.x, startY + delta.y, state.zoom)
        state.panX = clamped.x
        state.panY = clamped.y
        viewportCoordinator.updateCurrentPageFromViewport()
        engine.requestPlan()
        return Offset(clamped.x - startX, clamped.y - startY)
    }

    override fun zoomTo(zoom: Float, pivot: Offset) {
        val previousZoom = state.zoom
        if (zoom != previousZoom && previousZoom > 0f) {
            val ratio = zoom / previousZoom
            state.panX = pivot.x + (state.panX - pivot.x) * ratio
            state.panY = pivot.y + (state.panY - pivot.y) * ratio
            state.zoom = zoom
        }
        clampPanInPlace()
        viewportCoordinator.updateCurrentPageFromViewport()
        engine.requestPlan()
    }

    override fun setVelocity(velocity: Offset) {
        state.scrollVelocity = velocity
    }

    private fun clampPanInPlace() {
        val snapshot = viewportCoordinator.snapshot()
        if (snapshot.isEmpty) return
        val clamped = snapshot.clampPan(state.panX, state.panY, state.zoom)
        state.panX = clamped.x
        state.panY = clamped.y
    }

    // ------------------------------------------------------------------ gesture lifecycle

    override fun onGestureStart() {
        state.isGestureActive = true
    }

    override fun onGestureEnd() {
        state.isGestureActive = false
        clampPanInPlace()
        viewportCoordinator.updateCurrentPageFromViewport()
        engine.requestPlan()
    }

    // ------------------------------------------------------------------ animations

    override suspend fun transform(block: suspend () -> Unit) {
        transformMutex.mutate { block() }
    }

    override fun launchTransform(block: suspend () -> Unit): Job {
        val job = scope.launch { transformMutex.mutate { block() } }
        transformJob = job
        return job
    }

    override fun stopAnimations() {
        transformJob?.cancel()
        transformJob = null
        // Boot any suspend caller (programmatic animation) currently holding the mutex.
        scope.launch { transformMutex.mutate(MutatePriority.UserInput) {} }
    }

    override suspend fun animateZoomTo(
        targetZoom: Float,
        pivot: Offset?,
        animationSpec: AnimationSpec<Float>,
    ) {
        val currentConfig = config
        val target = targetZoom.coerceIn(currentConfig.minZoom, currentConfig.maxZoom)
        val resolvedPivot = pivot ?: Offset(viewportWidth / 2f, viewportHeight / 2f)
        transform {
            Animatable(state.zoom).animateTo(target, animationSpec) {
                zoomTo(value, resolvedPivot)
            }
        }
    }

    override suspend fun animatePanTo(
        targetX: Float,
        targetY: Float,
        animationSpec: AnimationSpec<Float>,
    ) {
        val startX = state.panX
        val startY = state.panY
        if (abs(targetX - startX) < 0.5f && abs(targetY - startY) < 0.5f) return
        transform {
            Animatable(0f).animateTo(1f, animationSpec) {
                state.panX = startX + (targetX - startX) * value
                state.panY = startY + (targetY - startY) * value
                clampPanInPlace()
                viewportCoordinator.updateCurrentPageFromViewport()
                engine.requestPlan()
            }
        }
    }

    // ------------------------------------------------------------------ environment

    override fun onViewportSizeChanged(width: Float, height: Float) {
        val before = viewportCoordinator.snapshot()
        if (!viewportCoordinator.updateViewport(width, height)) return
        val after = viewportCoordinator.snapshot()
        // Cached bitmaps depend on page layout sizes, not on the viewport itself. Keep them when
        // only the window changed (e.g. animated insets) and rebuild when pages resized.
        val layoutUnchanged =
            before.pageWidths.contentEquals(after.pageWidths) &&
                before.pageHeights.contentEquals(after.pageHeights)
        if (layoutUnchanged) {
            engine.requestPlan()
        } else {
            engine.invalidate()
        }
    }

    override fun updateConfig(newConfig: ResolvedViewerConfig) {
        if (config == newConfig) return
        val previous = config
        config = newConfig

        val layoutChanged =
            previous.fitMode != newConfig.fitMode ||
                previous.pageSpacingPx != newConfig.pageSpacingPx ||
                previous.scrollDirection != newConfig.scrollDirection
        if (layoutChanged) {
            viewportCoordinator.onLayoutInputsChanged()
            engine.invalidate()
        } else if (previous.renderQuality != newConfig.renderQuality) {
            engine.invalidate()
        } else {
            engine.requestPlan()
        }
    }

    override fun requestPlan() {
        engine.requestPlan()
    }

    override fun close() {
        engine.close()
        documentManager.close()
        scope.cancel()
    }

    companion object {
        /** Axis velocity (px/s) above which a snap release moves to the adjacent page. */
        const val SNAP_FLING_VELOCITY = 400f
    }
}
