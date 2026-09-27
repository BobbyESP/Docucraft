/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSelectionTest {

    // Page 0: "Alpha beta\ngamma"   Page 1: scanned, no text   Page 2: "delta epsilon\nzeta"
    private val pages: Map<Int, TextSelection?> =
        mapOf(
            0 to page(listOf("Alpha", "beta"), listOf("gamma")),
            1 to null,
            2 to page(listOf("delta", "epsilon"), listOf("zeta")),
        )

    @Test
    fun `carets order by page, then by reading order`() {
        assertTrue(TextCaret(0, 50) < TextCaret(1, 0))
        assertTrue(TextCaret(1, 0) < TextCaret(1, 1))
    }

    @Test
    fun `dragging backwards across pages selects the same text`() {
        assertEquals(
            DocumentSelection(TextCaret(0, 2), TextCaret(2, 3)),
            DocumentSelection.between(anchor = TextCaret(2, 3), focus = TextCaret(0, 2)),
        )
    }

    @Test
    fun `each page gets its share, the rest of the first, all of the middle, the start of the last`() {
        val selection = DocumentSelection(TextCaret(0, 2), TextCaret(2, 3))

        assertEquals(TextSpan(2, 16), selection.spanOn(page = 0, length = 16))
        assertEquals(TextSpan(0, 7), selection.spanOn(page = 1, length = 7))
        assertEquals(TextSpan(0, 3), selection.spanOn(page = 2, length = 18))
        assertNull(selection.spanOn(page = 3, length = 5))
    }

    @Test
    fun `copied text runs across pages, inside words, and leaves out a page without text`() {
        // From "pha" on page 0 to "del" on page 2.
        val selection = DocumentSelection(TextCaret(0, 2), TextCaret(2, 3))
        assertEquals("pha beta\ngamma\ndel", selection.text { pages[it] })
    }

    @Test
    fun `spaces a handle took in at either end are not copied`() {
        // From the space after "Alpha" to the line break after "beta".
        val selection = DocumentSelection(TextCaret(0, 5), TextCaret(0, 11))
        assertEquals("beta", selection.text { pages[it] })
    }

    @Test
    fun `a long press anchor stays selected whichever way the drag goes`() {
        val word = DocumentSelection(TextCaret(0, 6), TextCaret(0, 10)) // "beta"

        assertEquals(
            DocumentSelection(TextCaret(0, 6), TextCaret(2, 3)),
            DocumentSelection.extending(word, TextCaret(2, 3)),
        )
        assertEquals(
            DocumentSelection(TextCaret(0, 2), TextCaret(0, 10)),
            DocumentSelection.extending(word, TextCaret(0, 2)),
        )
        assertEquals(word, DocumentSelection.extending(word, TextCaret(0, 8)))
    }

    @Test
    fun `each page highlights only its share`() {
        val selection = DocumentSelection(TextCaret(0, 2), TextCaret(2, 3))
        val first = pages.getValue(0)!!
        val last = pages.getValue(2)!!

        assertEquals(first.highlightRects(TextSpan(2, 16)), selection.highlightRects(0, first))
        assertEquals(last.highlightRects(TextSpan(0, 3)), selection.highlightRects(2, last))
        assertEquals(emptyList<NormalizedRect>(), selection.highlightRects(3, last))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a selection cannot end before it starts`() {
        DocumentSelection(TextCaret(2, 0), TextCaret(0, 0))
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
