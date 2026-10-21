/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.widgets.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The scan widget cannot see how it ends up drawn, so the layout it is given has to be right from
 * the size alone. Text in a space too small for it is cut after a few letters, which is the failure
 * these sizes guard against.
 */
class ScanWidgetLayoutTest {

    @Test
    fun `one cell shows the icon alone`() {
        assertEquals(ScanWidgetLayout.Small, ScanWidgetLayout.forSize(57f, 70f))
    }

    /** A column of cells is still too narrow for the title, however tall it is. */
    @Test
    fun `a narrow widget shows the icon alone at any height`() {
        assertEquals(ScanWidgetLayout.Small, ScanWidgetLayout.forSize(73f, 400f))
    }

    @Test
    fun `a row of cells shows the title beside the icon`() {
        assertEquals(ScanWidgetLayout.Wide, ScanWidgetLayout.forSize(180f, 60f))
        assertEquals(ScanWidgetLayout.Wide, ScanWidgetLayout.forSize(380f, 129f))
    }

    @Test
    fun `room in both directions shows the title above the button`() {
        assertEquals(ScanWidgetLayout.Large, ScanWidgetLayout.forSize(130f, 130f))
        assertEquals(ScanWidgetLayout.Large, ScanWidgetLayout.forSize(380f, 300f))
    }
}
