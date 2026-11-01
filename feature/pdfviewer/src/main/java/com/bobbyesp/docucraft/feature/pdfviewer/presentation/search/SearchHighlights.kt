/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.search

import androidx.compose.runtime.Immutable
import com.bobbyesp.docucraft.feature.pdfviewer.domain.search.MatchRef
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.PageTextState
import com.bobbyesp.documentcontent.NormalizedRect

/**
 * What to light on one page: the boxes of every match, and, apart, those of the match the reader is
 * on, which is drawn stronger and over the rest.
 */
@Immutable
data class PageHighlights(val others: List<NormalizedRect>, val current: List<NormalizedRect>)

/**
 * Where the matches of a search are on the pages whose text is known, in page coordinates.
 *
 * A match is a stretch of a page's text; where it is on the page comes from that page's geometry,
 * which only the pages near the screen have. So this is worked out for those, once each time the
 * matches or the pages change, and the draw phase then only paints boxes: panning and zooming draw
 * again without working anything out.
 */
object SearchHighlights {

    /**
     * More matches than this on one page are not all lit. A one-letter query on a dense page
     * matches thousands of times, and a page of that is no longer one where "the match" can be
     * seen; the cap keeps the cost of typing that first letter bounded.
     */
    const val MaxPerPage = 500

    fun of(search: DocumentSearch?, pages: Map<Int, PageTextState>): Map<Int, PageHighlights> {
        if (search == null || search.matches.isEmpty) return emptyMap()
        val lit = HashMap<Int, PageHighlights>()
        for (page in search.matches.pages) {
            val text = pages[page] as? PageTextState.Text ?: continue
            val others = ArrayList<NormalizedRect>()
            val current = ArrayList<NormalizedRect>()
            search.matches.on(page).forEachIndexed { index, span ->
                val isCurrent = MatchRef(page, index) == search.active
                // The match the reader is on is always lit, however far down the page it is.
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
