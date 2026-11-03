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
 * The document is read page by page from where the reader is, so a search grows while [isScanning];
 * the counter is meaningful throughout because [matches] stay in reading order.
 *
 * @property query What was typed, as typed.
 * @property active The match the reader is on; `null` until one is found.
 * @property activeNumber Position of [active] among all matches, from 1; `0` if none.
 * @property scannedPages Pages read for this query.
 * @property pageCount Pages to read.
 * @property unsearchable Why a finished search found nothing to look in (images only, or a device
 *   that cannot read the text); `null` if the document has text.
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

    /** Whether there is a query to report on. */
    val isActive: Boolean
        get() = query.isNotBlank()
}
