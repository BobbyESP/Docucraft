/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.search

import com.bobbyesp.docucraft.feature.pdfviewer.domain.search.MatchRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.search.SearchMatches
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.selection.PageTextState
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageText
import com.bobbyesp.documentcontent.TextLine
import com.bobbyesp.documentcontent.TextSelection
import com.bobbyesp.documentcontent.TextSpan
import com.bobbyesp.documentcontent.TextWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHighlightsTest {

    // "ab cd ab": three words along one line, 0.1 apart.
    private val text =
        PageTextState.Text(
            TextSelection(
                PageText(
                    lines =
                        listOf(
                            TextLine(listOf(word("ab", 0f), word("cd", 0.1f), word("ab", 0.2f)))
                        ),
                    origin = ContentOrigin.EMBEDDED,
                )
            )
        )

    private fun word(text: String, x: Float) =
        TextWord(text, NormalizedRect(x, 0.1f, x + 0.08f, 0.14f))

    private fun search(active: MatchRef?, vararg spans: TextSpan, page: Int = 0) =
        DocumentSearch(
            query = "ab",
            matches = SearchMatches.None.with(page, spans.toList()),
            active = active,
        )

    @Test
    fun `the match the reader is on is lit apart from the others`() {
        val lit =
            SearchHighlights.of(
                search(MatchRef(0, 1), TextSpan(0, 2), TextSpan(6, 8)),
                mapOf(0 to text),
            )

        val page = lit.getValue(0)
        assertEquals(1, page.others.size)
        assertEquals(1, page.current.size)
        // The first "ab" is not the current one, so it is the one in `others`.
        assertTrue(page.others.single().right < page.current.single().left)
    }

    @Test
    fun `without a current match every match is an ordinary one`() {
        val lit =
            SearchHighlights.of(
                search(active = null, TextSpan(0, 2), TextSpan(6, 8)),
                mapOf(0 to text),
            )

        assertEquals(2, lit.getValue(0).others.size)
        assertTrue(lit.getValue(0).current.isEmpty())
    }

    @Test
    fun `a page whose text is not known yet is not lit`() {
        val lit = SearchHighlights.of(search(MatchRef(0, 0), TextSpan(0, 2)), pages = emptyMap())

        assertTrue(lit.isEmpty())
    }

    @Test
    fun `a page without text is not lit`() {
        val lit =
            SearchHighlights.of(
                search(MatchRef(0, 0), TextSpan(0, 2)),
                mapOf(0 to PageTextState.NoText),
            )

        assertTrue(lit.isEmpty())
    }

    @Test
    fun `no search lights nothing`() {
        assertTrue(SearchHighlights.of(null, mapOf(0 to text)).isEmpty())
        assertTrue(SearchHighlights.of(DocumentSearch(query = "ab"), mapOf(0 to text)).isEmpty())
    }

    @Test
    fun `a page with thousands of matches lights only some, but never loses the current one`() {
        val many = (0 until SearchHighlights.MaxPerPage + 50).map { TextSpan(0, 2) }
        val last = MatchRef(0, many.lastIndex)

        val page =
            SearchHighlights.of(search(last, *many.toTypedArray()), mapOf(0 to text)).getValue(0)

        assertEquals(SearchHighlights.MaxPerPage, page.others.size)
        assertEquals("the current match, past the cap, is still lit", 1, page.current.size)
    }
}
