/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSelectionTest {

    // Three lines, then a blank one, then a paragraph of one line:
    //   0 The   1 quick  2 brown           y 0.10..0.14
    //   3 fox   4 jumps                    y 0.16..0.20
    //   5 over  6 the    7 lazy            y 0.22..0.26
    //   (blank)
    //   8 Dog.                             y 0.34..0.38
    private val page =
        PageText(
            lines =
                listOf(
                    ltrLine(0.10f, "The", "quick", "brown"),
                    ltrLine(0.16f, "fox", "jumps"),
                    ltrLine(0.22f, "over", "the", "lazy"),
                    TextLine(emptyList()),
                    ltrLine(0.34f, "Dog."),
                ),
            origin = ContentOrigin.EMBEDDED,
        )
    private val selection = TextSelection(page)

    // ------------------------------------------------------------------ word under a point

    @Test
    fun `a point on a word is that word`() {
        assertEquals(1, selection.wordAt(centerOf(1)))
        assertEquals(6, selection.wordAt(centerOf(6)))
    }

    @Test
    fun `a point just off a word is still that word`() {
        val quick = selection.words[1].bounds
        assertEquals(
            1,
            selection.wordAt(NormalizedPoint(quick.left + 0.01f, quick.bottom + 0.005f)),
        )
    }

    @Test
    fun `a point far from every word is nothing`() {
        assertNull(selection.wordAt(NormalizedPoint(0.9f, 0.9f)))
    }

    @Test
    fun `a handle past the end of a line is at its last word`() {
        assertEquals(4, selection.nearestWord(NormalizedPoint(0.95f, 0.18f)))
    }

    @Test
    fun `a handle between lines is at the closest line`() {
        // 0.145 is just below line 0 (ends at 0.14) and further from line 1 (starts at 0.16).
        assertEquals(0, selection.nearestWord(NormalizedPoint(0.0f, 0.145f)))
    }

    @Test
    fun `a handle above or below the text is on the first or last line`() {
        assertEquals(0, selection.nearestWord(NormalizedPoint(0.0f, 0.0f)))
        assertEquals(8, selection.nearestWord(NormalizedPoint(0.9f, 0.99f)))
    }

    @Test
    fun `a handle in a blank line's gap goes to a line with words`() {
        val index = selection.nearestWord(NormalizedPoint(0.05f, 0.30f))
        assertTrue(index == 5 || index == 8)
    }

    // ------------------------------------------------------------------ ranges and reading order

    @Test
    fun `a range across lines holds every word between its ends`() {
        val range = selection.range(anchor = 1, focus = 6)
        assertEquals(WordRange(1, 6), range)
        assertEquals("quick brown\nfox jumps\nover the", selection.text(range))
    }

    @Test
    fun `dragging backwards selects the same words, in reading order`() {
        assertEquals(selection.range(1, 6), selection.range(anchor = 6, focus = 1))
        assertEquals("quick brown\nfox jumps\nover the", selection.text(selection.range(6, 1)))
    }

    @Test
    fun `a range of one word is that word`() {
        assertEquals("jumps", selection.text(selection.range(4, 4)))
    }

    // ------------------------------------------------------------------ copied text

    @Test
    fun `copied text keeps a blank line between paragraphs`() {
        assertEquals("lazy\n\nDog.", selection.text(selection.range(7, 8)))
    }

    @Test
    fun `all selects the whole page`() {
        val all = selection.all()
        assertEquals(WordRange(0, 8), all)
        assertEquals(
            "The quick brown\nfox jumps\nover the lazy\n\nDog.",
            selection.text(all!!),
        )
    }

    // ------------------------------------------------------------------ highlights

    @Test
    fun `the highlight is one rectangle per line, over the selected words`() {
        val rects = selection.highlightRects(selection.range(1, 6))

        assertEquals(3, rects.size)
        // Line 0 from "quick" to the end of "brown", gap included.
        assertEquals(selection.words[1].bounds.union(selection.words[2].bounds), rects[0])
        // Line 1 whole.
        assertEquals(selection.words[3].bounds.union(selection.words[4].bounds), rects[1])
        // Line 2 up to "the".
        assertEquals(selection.words[5].bounds.union(selection.words[6].bounds), rects[2])
    }

    @Test
    fun `a blank line has nothing to highlight`() {
        assertEquals(2, selection.highlightRects(selection.range(7, 8)).size)
    }

    // ------------------------------------------------------------------ pages without words

    @Test
    fun `a page with no words has nothing to select`() {
        val blank = TextSelection(PageText(listOf(TextLine(emptyList())), ContentOrigin.EMBEDDED))

        assertTrue(blank.words.isEmpty())
        assertNull(blank.all())
        assertNull(blank.wordAt(NormalizedPoint(0.5f, 0.5f)))
        assertNull(blank.nearestWord(NormalizedPoint(0.5f, 0.5f)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a range must be of words on the page`() {
        selection.range(0, 9)
    }

    // ------------------------------------------------------------------ right to left

    @Test
    fun `a right-to-left line is read, copied and dragged in reading order`() {
        // Reading order: אחת, שתיים, שלוש, laid out from the right edge leftwards.
        val rtl =
            TextSelection(
                PageText(
                    lines =
                        listOf(
                            TextLine(
                                listOf(
                                    word("אחת", left = 0.80f, top = 0.1f),
                                    word("שתיים", left = 0.60f, top = 0.1f),
                                    word("שלוש", left = 0.40f, top = 0.1f),
                                )
                            )
                        ),
                    origin = ContentOrigin.EMBEDDED,
                )
            )

        // The rightmost word is the first read.
        assertEquals(0, rtl.wordAt(NormalizedPoint(0.85f, 0.12f)))
        // Past the left end of the line is its last word in reading order.
        assertEquals(2, rtl.nearestWord(NormalizedPoint(0.05f, 0.12f)))
        // Dragging from left to right on screen still copies in reading order.
        val range = rtl.range(anchor = 2, focus = 0)
        assertEquals("אחת שתיים שלוש", rtl.text(range))
        assertEquals(
            listOf(NormalizedRect(0.40f, 0.1f, 0.80f + WordWidth, 0.1f + LineHeight)),
            rtl.highlightRects(range),
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun centerOf(index: Int): NormalizedPoint {
        val b = selection.words[index].bounds
        return NormalizedPoint((b.left + b.right) / 2, (b.top + b.bottom) / 2)
    }

    private companion object {
        const val WordWidth = 0.10f
        const val WordGap = 0.02f
        const val LineHeight = 0.04f

        /** Words laid out from the left margin, left to right. */
        fun ltrLine(top: Float, vararg texts: String): TextLine =
            TextLine(
                texts.mapIndexed { i, text ->
                    word(text, left = 0.05f + i * (WordWidth + WordGap), top = top)
                }
            )

        fun word(text: String, left: Float, top: Float): TextWord =
            TextWord(text, NormalizedRect(left, top, left + WordWidth, top + LineHeight))
    }
}
