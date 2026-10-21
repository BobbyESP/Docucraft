/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.widgets.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The recent-documents widget does not scroll, so how many rows it is given is how many it shows: a
 * row too many is cut in half at the bottom, a row too few leaves a hole.
 */
class RecentWidgetLayoutTest {

    @Test
    fun `the shortest widget still shows a document`() {
        assertEquals(1, RecentWidgetLayout.rowsFor(heightDp = 110f, most = 10))
    }

    @Test
    fun `a taller widget shows the rows that fit whole`() {
        // 8 + 40 + 3 x 56 + 8 = 224
        assertEquals(3, RecentWidgetLayout.rowsFor(heightDp = 226f, most = 10))
        assertEquals(2, RecentWidgetLayout.rowsFor(heightDp = 223f, most = 10))
    }

    @Test
    fun `no more rows than there are documents to ask for`() {
        assertEquals(4, RecentWidgetLayout.rowsFor(heightDp = 900f, most = 4))
    }
}
