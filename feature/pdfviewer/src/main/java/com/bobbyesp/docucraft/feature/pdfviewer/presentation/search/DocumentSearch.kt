/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.search

import com.bobbyesp.docucraft.feature.pdfviewer.domain.search.MatchRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.search.SearchMatches
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.TextUnavailable

/**
 * A search in the open document, as the screen shows it.
 *
 * A document is read page by page, starting where the reader is, so a search is a thing that
 * *happens*: what it has found grows, and [isScanning] says whether it is done. The counter is
 * meaningful all the while, because [matches] are kept in reading order whichever page was read
 * first.
 *
 * @property query What was typed, as it was.
 * @property active The match the reader is on; `null` until the first one is found.
 * @property activeNumber Which match of the whole document [active] is, counting from 1; `0` when
 *   there is none.
 * @property scannedPages How many pages have been read for this query.
 * @property pageCount How many there are to read.
 * @property unsearchable Why a search that is over found nothing to look in, when the cause is not
 *   the query but the document: it is made of images, or the device cannot read its text. `null`
 *   when the document has text.
 */
data class DocumentSearch(
    val query: String,
    val matches: SearchMatches = SearchMatches.None,
    val active: MatchRef? = null,
    val activeNumber: Int = 0,
    val scannedPages: Int = 0,
    val pageCount: Int = 0,
    val unsearchable: TextUnavailable? = null,
) {
    /** How many matches have been found so far. */
    val total: Int
        get() = matches.total

    /** Whether the document is still being read. */
    val isScanning: Boolean
        get() = scannedPages < pageCount

    /** The share of the document read so far, from 0 to 1. */
    val progress: Float
        get() = if (pageCount == 0) 1f else scannedPages.toFloat() / pageCount

    /** Whether there is something to look for, and so something to say about how it went. */
    val isActive: Boolean
        get() = query.isNotBlank()
}
