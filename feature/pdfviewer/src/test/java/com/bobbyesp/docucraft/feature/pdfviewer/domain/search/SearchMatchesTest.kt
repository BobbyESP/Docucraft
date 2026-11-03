/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.search

import com.bobbyesp.documentcontent.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatchesTest {

    // Page 1 has two matches, page 4 has one, page 7 has three.
    private val matches =
        SearchMatches.None.with(7, spans(5, 20, 40)).with(1, spans(3, 30)).with(4, spans(10))

    private fun spans(vararg starts: Int) = starts.map { TextSpan(it, it + 2) }

    @Test
    fun `matches are counted in reading order whatever order the pages were found in`() {
        assertEquals(6, matches.total)
        assertEquals(setOf(1, 4, 7), matches.pages)
        assertEquals(1, matches.numberOf(MatchRef(1, 0)))
        assertEquals(2, matches.numberOf(MatchRef(1, 1)))
        assertEquals(3, matches.numberOf(MatchRef(4, 0)))
        assertEquals(6, matches.numberOf(MatchRef(7, 2)))
    }

    @Test
    fun `a match that does not exist has no number`() {
        assertEquals(0, matches.numberOf(MatchRef(1, 2)))
        assertEquals(0, matches.numberOf(MatchRef(2, 0)))
    }

    @Test
    fun `finding a page changes nothing for the ones before it`() {
        val more = matches.with(0, spans(1))

        // The first match of the document is a new one, and the old ones move down by it.
        assertEquals(1, more.numberOf(MatchRef(0, 0)))
        assertEquals(2, more.numberOf(MatchRef(1, 0)))
        assertEquals(7, more.total)
    }

    @Test
    fun `adding a page gives new matches and leaves the old ones as they were`() {
        val more = matches.with(9, spans(1))

        assertEquals(6, matches.total)
        assertEquals(7, more.total)
    }

    @Test
    fun `a page with no match is not added`() {
        assertSame(matches, matches.with(2, emptyList()))
    }

    @Test
    fun `the first match from a point is the first at or after it`() {
        assertEquals(MatchRef(1, 1), matches.firstFrom(page = 1, offset = 4))
        assertEquals(MatchRef(1, 0), matches.firstFrom(page = 1, offset = 3))
    }

    @Test
    fun `past the last match of a page, the next page with one`() {
        assertEquals(MatchRef(4, 0), matches.firstFrom(page = 1, offset = 31))
        assertEquals(MatchRef(4, 0), matches.firstFrom(page = 2, offset = 0))
    }

    @Test
    fun `after the last match of the document there is none to start from`() {
        assertNull(matches.firstFrom(page = 7, offset = 41))
        assertNull(matches.firstFrom(page = 8, offset = 0))
    }

    @Test
    fun `the first match is the first of the first page`() {
        assertEquals(MatchRef(1, 0), matches.first())
        assertNull(SearchMatches.None.first())
    }

    @Test
    fun `next walks the matches in reading order`() {
        val walked = generateSequence(matches.first()) { matches.next(it) }.take(6).toList()

        assertEquals(
            listOf(
                MatchRef(1, 0),
                MatchRef(1, 1),
                MatchRef(4, 0),
                MatchRef(7, 0),
                MatchRef(7, 1),
                MatchRef(7, 2),
            ),
            walked,
        )
    }

    @Test
    fun `next after the last goes back to the first`() {
        assertEquals(MatchRef(1, 0), matches.next(MatchRef(7, 2)))
    }

    @Test
    fun `previous walks back, and from the first goes to the last`() {
        assertEquals(MatchRef(1, 1), matches.previous(MatchRef(4, 0)))
        assertEquals(MatchRef(4, 0), matches.previous(MatchRef(7, 0)))
        assertEquals(MatchRef(7, 2), matches.previous(MatchRef(1, 0)))
    }

    @Test
    fun `without a current match, next is the first and previous the last`() {
        assertEquals(MatchRef(1, 0), matches.next(null))
        assertEquals(MatchRef(7, 2), matches.previous(null))
    }

    @Test
    fun `a stale reference is treated as having none`() {
        // The matches changed under it: it points at nothing.
        assertEquals(MatchRef(1, 0), matches.next(MatchRef(3, 0)))
        assertEquals(MatchRef(7, 2), matches.previous(MatchRef(3, 0)))
    }

    @Test
    fun `with nothing found there is nowhere to go`() {
        assertTrue(SearchMatches.None.isEmpty)
        assertNull(SearchMatches.None.next(null))
        assertNull(SearchMatches.None.previous(null))
    }

    @Test
    fun `a single match steps onto itself`() {
        val one = SearchMatches.None.with(2, spans(9))

        assertEquals(MatchRef(2, 0), one.next(MatchRef(2, 0)))
        assertEquals(MatchRef(2, 0), one.previous(MatchRef(2, 0)))
    }
}
