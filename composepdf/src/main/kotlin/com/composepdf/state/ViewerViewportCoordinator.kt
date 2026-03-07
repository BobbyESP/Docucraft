package com.composepdf.state

import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.composepdf.layout.PageLayoutSnapshot
import com.composepdf.renderer.tiles.ViewportState

/**
 * Manages the spatial and geometric state of the PDF viewer, acting as the bridge between
 * the viewport dimensions, page layouts, and the document's physical scroll position.
 *
 * This coordinator is responsible for:
 * - Tracking viewport dimensions and maintaining the [PageLayoutSnapshot].
 * - Computing pan constraints (clamping) to keep the view within document boundaries.
 * - Mapping screen coordinates to page-relative positions.
 * - Calculating optimal zoom levels for "fit-to-width" or "fit-to-page" modes.
 * - Determining the current active page based on the viewport's center point.
 *
 * It serves as a specialized collaborator for the controller, isolating geometric calculations
 * from document loading and rendering logic.
 */
internal class ViewerViewportCoordinator(
    private val state: PdfViewerState,
    private val configProvider: () -> ViewerConfig,
    private val snapshotFactory: (List<Size>, Float, Float, FitMode, Float) -> PageLayoutSnapshot =
        PageLayoutSnapshot::build
) {
    var viewportWidth by mutableFloatStateOf(0f)
        private set

    var viewportHeight by mutableFloatStateOf(0f)
        private set

    var pageSizes: List<Size> by mutableStateOf(emptyList())
        private set

    private var layoutSnapshot by mutableStateOf(PageLayoutSnapshot.empty())
    private var layoutVersion by mutableIntStateOf(0)

    val hasLayout: Boolean get() = !layoutSnapshot.isEmpty

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

    fun visiblePageIndices(): IntRange {
        layoutVersion
        return layoutSnapshot.visiblePageIndices(state.panY, state.zoom)
    }

    fun isPointOverPage(point: Offset): Boolean = layoutSnapshot.isPointOverPage(
        screenX = point.x,
        screenY = point.y,
        panX = state.panX,
        panY = state.panY,
        zoom = state.zoom
    )

    fun clampPan() {
        val clampedPan = layoutSnapshot.clampPan(state.panX, state.panY, state.zoom)
        state.panX = clampedPan.x
        state.panY = clampedPan.y
    }

    fun computeCenteredPanForPage(pageIndex: Int): Pair<Float, Float> {
        val centeredPan = layoutSnapshot.centeredPanForPage(pageIndex, state.zoom)
        return centeredPan.x to centeredPan.y
    }

    fun computeFitDocumentZoom(): Float {
        val config = configProvider()
        return layoutSnapshot.fitDocumentZoom(config.fitMode, config.minZoom, config.maxZoom)
    }

    fun computeFitPageZoom(pageIndex: Int = state.currentPage): Float {
        val config = configProvider()
        return layoutSnapshot.fitPageZoom(pageIndex, config.fitMode, config.minZoom, config.maxZoom)
    }

    fun updateCurrentPageFromViewport() {
        val currentPage = layoutSnapshot.currentPageAtViewportCenter(state.panY, state.zoom) ?: return
        state.currentPage = currentPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
    }

    fun viewportState(): ViewportState = ViewportState(
        width = viewportWidth,
        height = viewportHeight,
        panX = state.panX,
        panY = state.panY
    )

    fun snapshot(): PageLayoutSnapshot = layoutSnapshot

    private fun rebuildLayoutSnapshot() {
        val config = configProvider()
        layoutSnapshot = snapshotFactory(
            pageSizes,
            viewportWidth,
            viewportHeight,
            config.fitMode,
            config.pageSpacingPx
        )
        layoutVersion++
    }
}

