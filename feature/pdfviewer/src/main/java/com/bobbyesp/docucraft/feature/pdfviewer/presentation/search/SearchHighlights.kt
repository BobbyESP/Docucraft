/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.search

import androidx.compose.runtime.Immutable
import com.bobbyesp.docucraft.feature.pdfviewer.domain.search.MatchRef
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.PageTextState
import com.bobbyesp.documentcontent.NormalizedRect

/** The boxes to light on one page: [others] for every match, [current] for the active one. */
@Immutable
data class PageHighlights(val others: List<NormalizedRect>, val current: List<NormalizedRect>)

/**
 * Where a search's matches are, in page coordinates, on the pages whose geometry is known.
 *
 * Computed once when the matches or the pages change, so the draw phase only paints boxes and
 * panning or zooming computes nothing.
 */
object SearchHighlights {

    /**
     * Most matches lit on one page. A one-letter query on a dense page matches thousands of times;
     * the cap bounds the cost of typing that first letter.
     */
    const val MaxPerPage = 500

    /** The highlights of [search] by page index; the active match is lit even past [MaxPerPage]. */
    fun of(search: DocumentSearch?, pages: Map<Int, PageTextState>): Map<Int, PageHighlights> {
        if (search == null || search.matches.isEmpty) return emptyMap()
        val lit = HashMap<Int, PageHighlights>()
        for (page in search.matches.pages) {
            val text = pages[page] as? PageTextState.Text ?: continue
            val others = ArrayList<NormalizedRect>()
            val current = ArrayList<NormalizedRect>()
            search.matches.on(page).forEachIndexed { index, span ->
                val isCurrent = MatchRef(page, index) == search.active
                if (isCurrent || index < MaxPerPage) {
                    val boxes = text.selection.highlightRects(span)
                    if (isCurrent) current += boxes else others += boxes
                }
            }
            lit[page] = PageHighlights(others, current)
        }
        return lit
    }
}
