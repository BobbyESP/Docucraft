/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.logic

import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.composepdf.FitMode
import com.composepdf.PdfViewerState
import com.composepdf.ScrollDirection

/**
 * Manages the spatial and geometric state of the PDF viewer, acting as the bridge between the
 * viewport dimensions, page layouts, and the document's physical scroll position.
 */
internal class ViewerViewportCoordinator(
    private val state: PdfViewerState,
    private val configProvider: () -> ResolvedViewerConfig,
    private val snapshotFactory:
        (List<Size>, Float, Float, FitMode, Float, ScrollDirection) -> PageLayoutSnapshot =
        PageLayoutSnapshot::build,
) {
    var viewportWidth by mutableFloatStateOf(0f)
        private set

    var viewportHeight by mutableFloatStateOf(0f)
        private set

    var pageSizes: List<Size> by mutableStateOf(emptyList())
        private set

    private var layoutSnapshot by mutableStateOf(PageLayoutSnapshot.empty())
    private var layoutVersion by mutableIntStateOf(0)

    val hasLayout: Boolean
        get() = !layoutSnapshot.isEmpty

    fun updateViewport(width: Float, height: Float): Boolean {
        if (width == viewportWidth && height == viewportHeight) return false

        viewportWidth = width
        viewportHeight = height
        rebuildLayoutSnapshot()
        clampPan()
        updateCurrentPageFromViewport()
        return true
    }

    fun updatePageSizes(sizes: List<Size>) {
        pageSizes = sizes
        rebuildLayoutSnapshot()
        clampPan()
        updateCurrentPageFromViewport()
    }

    fun onLayoutInputsChanged() {
        rebuildLayoutSnapshot()
        clampPan()
        updateCurrentPageFromViewport()
    }

    fun pageHeightPx(index: Int): Float = layoutSnapshot.pageHeightPx(index)

    fun pageWidthPx(index: Int): Float = layoutSnapshot.pageWidthPx(index)

    fun pageTopDocY(index: Int): Float = layoutSnapshot.pageTopDocY(index)

    fun pageLeftDocX(index: Int): Float = layoutSnapshot.pageLeftDocX(index)

    fun visiblePageIndices(): IntRange {
        layoutVersion
        return layoutSnapshot.visiblePageIndices(state.panX, state.panY, state.zoom)
    }

    fun clampPan() {
        val clampedPan = layoutSnapshot.clampPan(state.panX, state.panY, state.zoom)
        state.panX = clampedPan.x
        state.panY = clampedPan.y
    }

    fun centeredPanForPage(pageIndex: Int): PanPosition =
        layoutSnapshot.centeredPanForPage(pageIndex, state.zoom)

    fun computeFitDocumentZoom(): Float {
        val config = configProvider()
        return layoutSnapshot.fitDocumentZoom(config.fitMode, config.minZoom, config.maxZoom)
    }

    fun computeFitPageZoom(pageIndex: Int = state.currentPage): Float {
        val config = configProvider()
        return layoutSnapshot.fitPageZoom(pageIndex, config.fitMode, config.minZoom, config.maxZoom)
    }

    fun updateCurrentPageFromViewport() {
        val currentPage =
            layoutSnapshot.currentPageAtViewportCenter(state.panX, state.panY, state.zoom) ?: return
        state.currentPage = currentPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
    }

    fun snapshot(): PageLayoutSnapshot = layoutSnapshot

    private fun rebuildLayoutSnapshot() {
        val config = configProvider()
        layoutSnapshot =
            snapshotFactory(
                pageSizes,
                viewportWidth,
                viewportHeight,
                config.fitMode,
                config.pageSpacingPx,
                config.scrollDirection,
            )
        layoutVersion++
    }
}
