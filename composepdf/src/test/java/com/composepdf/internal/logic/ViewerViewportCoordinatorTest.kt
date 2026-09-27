/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.logic

import android.util.Size
import com.composepdf.PdfViewerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerViewportCoordinatorTest {

    @Test
    fun updateViewport_rebuildsLayoutAndExposesQueries() {
        val state =
            PdfViewerState().apply {
                pageCount = 3
                zoom = 1f
                panX = 0f
                panY = -520f
            }
        val coordinator =
            ViewerViewportCoordinator(
                state = state,
                configProvider = { ResolvedViewerConfig() },
                snapshotFactory = { _, viewport, _, _, _ -> threePages(viewport) },
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
        val state =
            PdfViewerState().apply {
                pageCount = 2
                zoom = 1f
                panX = -50f
                panY = -700f
            }
        val coordinator =
            ViewerViewportCoordinator(
                state = state,
                configProvider = { ResolvedViewerConfig() },
                snapshotFactory = { _, viewport, _, _, _ ->
                    layoutOf(
                        widths = floatArrayOf(500f, 500f),
                        heights = floatArrayOf(500f, 500f),
                        spacing = 20f,
                        viewport = viewport,
                    )
                },
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
        val config = ResolvedViewerConfig(minZoom = 0.5f, maxZoom = 4f)
        val coordinator =
            ViewerViewportCoordinator(
                state = state,
                configProvider = { config },
                snapshotFactory = { _, viewport, _, _, _ ->
                    layoutOf(
                        widths = floatArrayOf(250f),
                        heights = floatArrayOf(500f),
                        spacing = 0f,
                        viewport = viewport,
                    )
                },
            )

        coordinator.updatePageSizes(List(1) { Size(1, 1) })
        coordinator.updateViewport(500f, 1000f)

        assertEquals(2f, coordinator.computeFitDocumentZoom(), 0.001f)
        assertEquals(2f, coordinator.computeFitPageZoom(0), 0.001f)
    }
}
