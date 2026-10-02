/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

/**
 * Where the reader left a document: a page, and how far along it.
 *
 * @property pageIndex Zero-based.
 * @property offset From 0, the start of the page, to 1, its end.
 */
data class ReadingPosition(val pageIndex: Int, val offset: Float) {

    /**
     * This position in a document of [pageCount] pages, which is always a place the document has.
     *
     * A document can change between two readings, another app's most of all. A position on a page
     * it no longer has becomes the start of its last page, rather than being thrown away: the
     * reader was near the end. With an unknown [pageCount] the page is taken as it is.
     */
    fun within(pageCount: Int?): ReadingPosition {
        val page = pageIndex.coerceAtLeast(0)
        if (pageCount != null && pageCount > 0 && page >= pageCount) {
            return ReadingPosition(pageIndex = pageCount - 1, offset = 0f)
        }
        // A number that is not one compares false with everything, and would pass a range check.
        val along = if (offset.isNaN()) 0f else offset.coerceIn(0f, 1f)
        return ReadingPosition(page, along)
    }
}
