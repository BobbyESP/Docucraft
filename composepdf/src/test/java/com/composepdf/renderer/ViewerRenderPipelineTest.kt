package com.composepdf.renderer

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerRenderPipelineTest {

    @Test
    fun selectBasePageRenderZoom_usesCurrentZoomBelowTileThreshold() {
        assertEquals(1.05f, selectBasePageRenderZoom(currentZoom = 1.05f, steppedZoom = 1.25f), 0.001f)
        assertEquals(1.1f, selectBasePageRenderZoom(currentZoom = 1.1f, steppedZoom = 1.25f), 0.001f)
    }

    @Test
    fun selectBasePageRenderZoom_usesSteppedZoomWhenTilesAreActive() {
        assertEquals(1.25f, selectBasePageRenderZoom(currentZoom = 1.26f, steppedZoom = 1.25f), 0.001f)
        assertEquals(1.77f, selectBasePageRenderZoom(currentZoom = 1.9f, steppedZoom = 1.77f), 0.001f)
    }
}

