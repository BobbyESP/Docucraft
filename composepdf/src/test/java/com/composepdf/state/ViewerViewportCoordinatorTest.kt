package com.composepdf.state

import android.util.Size
import com.composepdf.layout.PageLayoutSnapshot
import com.composepdf.layout.ViewportMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerViewportCoordinatorTest {

    @Test
    fun updateViewport_rebuildsLayoutAndExposesQueries() {
        val state = PdfViewerState().apply {
            pageCount = 3
            zoom = 1f
            panX = 0f
            panY = -520f
        }
        val coordinator = ViewerViewportCoordinator(
            state = state,
            configProvider = { ViewerConfig() },
            snapshotFactory = { pageSizes, viewportWidth, viewportHeight, _, pageSpacingPx ->
                fakeSnapshot(
                    pageCount = pageSizes.size,
                    viewportWidth = viewportWidth,
                    viewportHeight = viewportHeight,
                    pageSpacingPx = pageSpacingPx
                )
            }
        )

        coordinator.updatePageSizes(List(3) { Size(1, 1) })
        val changed = coordinator.updateViewport(500f, 500f)

        assertTrue(changed)
        assertEquals(500f, coordinator.viewportWidth, 0.001f)
        assertEquals(500f, coordinator.viewportHeight, 0.001f)
        assertEquals(1..1, coordinator.visiblePageIndices())
        assertEquals(520f, coordinator.pageTopDocY(1), 0.001f)
    }

    @Test
    fun clampPan_andCurrentPage_areDelegatedToSnapshotGeometry() {
        val state = PdfViewerState().apply {
            pageCount = 2
            zoom = 1f
            panX = -50f
            panY = -700f
        }
        val coordinator = ViewerViewportCoordinator(
            state = state,
            configProvider = { ViewerConfig() },
            snapshotFactory = { _, viewportWidth, viewportHeight, _, _ ->
                PageLayoutSnapshot(
                    pageSizes = listOf(Size(1, 1), Size(1, 1)),
                    pageTops = floatArrayOf(0f, 520f),
                    pageHeights = floatArrayOf(500f, 500f),
                    pageWidths = floatArrayOf(500f, 500f),
                    totalDocumentHeight = 1020f,
                    maxPageWidth = 500f,
                    viewport = ViewportMetrics(viewportWidth, viewportHeight),
                    pageSpacingPx = 20f
                )
            }
        )

        coordinator.updatePageSizes(List(2) { Size(1, 1) })
        coordinator.updateViewport(500f, 500f)
        coordinator.clampPan()
        coordinator.updateCurrentPageFromViewport()

        assertEquals(0f, state.panX, 0.001f)
        assertEquals(1, state.currentPage)
    }

    @Test
    fun computeFitZooms_delegateToSnapshotUsingCurrentConfig() {
        val state = PdfViewerState().apply { pageCount = 1 }
        val config = ViewerConfig(minZoom = 0.5f, maxZoom = 4f)
        val coordinator = ViewerViewportCoordinator(
            state = state,
            configProvider = { config },
            snapshotFactory = { _, viewportWidth, viewportHeight, _, _ ->
                PageLayoutSnapshot(
                    pageSizes = listOf(Size(1, 1)),
                    pageTops = floatArrayOf(0f),
                    pageHeights = floatArrayOf(500f),
                    pageWidths = floatArrayOf(250f),
                    totalDocumentHeight = 500f,
                    maxPageWidth = 250f,
                    viewport = ViewportMetrics(viewportWidth, viewportHeight),
                    pageSpacingPx = 0f
                )
            }
        )

        coordinator.updatePageSizes(List(1) { Size(1, 1) })
        coordinator.updateViewport(500f, 1000f)

        assertEquals(2f, coordinator.computeFitDocumentZoom(), 0.001f)
        assertEquals(2f, coordinator.computeFitPageZoom(0), 0.001f)
    }

    private fun fakeSnapshot(
        pageCount: Int,
        viewportWidth: Float,
        viewportHeight: Float,
        pageSpacingPx: Float
    ) = PageLayoutSnapshot(
        pageSizes = List(pageCount) { Size(1, 1) },
        pageTops = floatArrayOf(0f, 520f, 1040f),
        pageHeights = floatArrayOf(500f, 500f, 500f),
        pageWidths = floatArrayOf(500f, 500f, 500f),
        totalDocumentHeight = 1540f,
        maxPageWidth = 500f,
        viewport = ViewportMetrics(viewportWidth, viewportHeight),
        pageSpacingPx = pageSpacingPx
    )
}

