/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.engine

import com.composepdf.internal.engine.ZoomSteps
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomStepsTest {

    @Test
    fun levelZero_atZoomOne() {
        assertEquals(0, ZoomSteps.levelFor(1f))
        assertEquals(1f, ZoomSteps.scaleFor(0), 1e-4f)
    }

    @Test
    fun levels_areCeiledSoScaleAlwaysCoversZoom() {
        for (zoom in floatArrayOf(0.3f, 0.75f, 1.2f, 1.5f, 2.2f, 3.7f, 5.9f, 10f)) {
            val level = ZoomSteps.levelFor(zoom)
            val scale = ZoomSteps.scaleFor(level)
            assertTrue("scale $scale should cover zoom $zoom", scale >= zoom * 0.999f)
            // And the previous step should not cover it (tightness).
            val previous = ZoomSteps.scaleFor(level - 1)
            assertTrue("level for $zoom should be tight", previous < zoom)
        }
    }

    @Test
    fun exactStepBoundaries_doNotBumpToNextLevel() {
        val sqrt2 = sqrt(2.0).toFloat()
        assertEquals(1, ZoomSteps.levelFor(sqrt2))
        assertEquals(2, ZoomSteps.levelFor(2f))
        assertEquals(4, ZoomSteps.levelFor(4f))
        assertEquals(-2, ZoomSteps.levelFor(0.5f))
    }

    @Test
    fun negativeLevels_coverZoomOut() {
        val level = ZoomSteps.levelFor(0.25f)
        assertEquals(-4, level)
        assertEquals(0.25f, ZoomSteps.scaleFor(level), 1e-4f)
    }

    @Test
    fun degenerateZoom_clampsToMinLevel() {
        assertEquals(ZoomSteps.MIN_LEVEL, ZoomSteps.levelFor(0f))
        assertEquals(ZoomSteps.MIN_LEVEL, ZoomSteps.levelFor(-1f))
    }
}
