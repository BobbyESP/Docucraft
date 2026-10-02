/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** A position kept for a document is always read as a place that document has. */
class ReadingPositionTest {

    @Test
    fun `a position inside the document is left alone`() {
        assertEquals(ReadingPosition(3, 0.4f), ReadingPosition(3, 0.4f).within(pageCount = 10))
    }

    /** Another app's file can be replaced by a shorter one between two readings. */
    @Test
    fun `a page the document no longer has becomes the start of its last page`() {
        assertEquals(ReadingPosition(4, 0f), ReadingPosition(9, 0.7f).within(pageCount = 5))
        assertEquals(ReadingPosition(4, 0f), ReadingPosition(5, 0.7f).within(pageCount = 5))
    }

    @Test
    fun `the last page is still inside`() {
        assertEquals(ReadingPosition(4, 0.7f), ReadingPosition(4, 0.7f).within(pageCount = 5))
    }

    /** An old scan can be in the catalogue without a page count. */
    @Test
    fun `with an unknown page count the page is taken as it is`() {
        assertEquals(ReadingPosition(40, 0.2f), ReadingPosition(40, 0.2f).within(pageCount = null))
    }

    @Test
    fun `what is not a position becomes the nearest one`() {
        assertEquals(ReadingPosition(0, 0f), ReadingPosition(-2, -0.5f).within(pageCount = 5))
        assertEquals(ReadingPosition(1, 1f), ReadingPosition(1, 7f).within(pageCount = 5))
        assertEquals(ReadingPosition(1, 0f), ReadingPosition(1, Float.NaN).within(pageCount = 5))
    }
}
