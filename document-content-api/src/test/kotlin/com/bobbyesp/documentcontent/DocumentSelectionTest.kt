/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSelectionTest {

    // Page 0: "Alpha beta" / "gamma"   Page 1: scanned, no text   Page 2: "delta epsilon" / "zeta"
    private val pages: Map<Int, TextSelection?> =
        mapOf(
            0 to page(listOf("Alpha", "beta"), listOf("gamma")),
            1 to null,
            2 to page(listOf("delta", "epsilon"), listOf("zeta")),
        )

    @Test
    fun `positions order by page, then by reading order`() {
        assertTrue(TextPosition(0, 5) < TextPosition(1, 0))
        assertTrue(TextPosition(1, 0) < TextPosition(1, 1))
    }

    @Test
    fun `dragging backwards across pages selects the same words`() {
        assertEquals(
            DocumentSelection(TextPosition(0, 1), TextPosition(2, 1)),
            DocumentSelection.between(anchor = TextPosition(2, 1), focus = TextPosition(0, 1)),
        )
    }

    @Test
    fun `each page gets its share, the rest of the first, all of the middle, the start of the last`() {
        val selection = DocumentSelection(TextPosition(0, 1), TextPosition(2, 1))

        assertEquals(WordRange(1, 2), selection.rangeOn(page = 0, wordCount = 3))
        assertEquals(WordRange(0, 6), selection.rangeOn(page = 1, wordCount = 7))
        assertEquals(WordRange(0, 1), selection.rangeOn(page = 2, wordCount = 3))
        assertNull(selection.rangeOn(page = 3, wordCount = 3))
    }

    @Test
    fun `a page without words has no share`() {
        val selection = DocumentSelection(TextPosition(0, 0), TextPosition(2, 0))
        assertNull(selection.rangeOn(page = 1, wordCount = 0))
    }

    @Test
    fun `copied text runs across pages and leaves out a page without text`() {
        val selection = DocumentSelection(TextPosition(0, 1), TextPosition(2, 1))
        assertEquals("beta\ngamma\ndelta epsilon", selection.text { pages[it] })
    }

    @Test
    fun `a selection within one page copies like the page's own`() {
        val selection = DocumentSelection(TextPosition(2, 0), TextPosition(2, 2))
        assertEquals("delta epsilon\nzeta", selection.text { pages[it] })
    }

    @Test
    fun `each page highlights only its share`() {
        val selection = DocumentSelection(TextPosition(0, 1), TextPosition(2, 0))
        val first = pages.getValue(0)!!
        val last = pages.getValue(2)!!

        assertEquals(first.highlightRects(WordRange(1, 2)), selection.highlightRects(0, first))
        assertEquals(last.highlightRects(WordRange(0, 0)), selection.highlightRects(2, last))
        assertEquals(emptyList<NormalizedRect>(), selection.highlightRects(3, last))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a selection cannot end before it starts`() {
        DocumentSelection(TextPosition(2, 0), TextPosition(0, 0))
    }

    private fun page(vararg lines: List<String>): TextSelection =
        TextSelection(
            PageText(
                lines =
                    lines.mapIndexed { row, words ->
                        TextLine(
                            words.mapIndexed { column, text ->
                                val left = 0.1f + column * 0.2f
                                val top = 0.1f + row * 0.05f
                                TextWord(text, NormalizedRect(left, top, left + 0.15f, top + 0.04f))
                            }
                        )
                    },
                origin = ContentOrigin.EMBEDDED,
            )
        )
}
