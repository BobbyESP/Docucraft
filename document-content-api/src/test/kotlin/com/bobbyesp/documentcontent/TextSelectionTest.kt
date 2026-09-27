/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSelectionTest {

    // Monospaced, 0.02 wide a character, 0.02 between words, lines 0.04 tall:
    //   "The quick brown"      y 0.10..0.14
    //   "fox jumps"            y 0.16..0.20
    //   "over the lazy"        y 0.22..0.26
    //   (blank)
    //   "Dog."                 y 0.34..0.38
    private val page =
        PageText(
            lines =
                listOf(
                    line(0.10f, "The", "quick", "brown"),
                    line(0.16f, "fox", "jumps"),
                    line(0.22f, "over", "the", "lazy"),
                    TextLine(emptyList()),
                    line(0.34f, "Dog."),
                ),
            origin = ContentOrigin.EMBEDDED,
        )
    private val selection = TextSelection(page)

    // ------------------------------------------------------------------ the page's text

    @Test
    fun `the page text is what would be pasted`() {
        assertEquals("The quick brown\nfox jumps\nover the lazy\n\nDog.", selection.text)
    }

    // ------------------------------------------------------------------ long press: a word

    @Test
    fun `a long press on a word selects the whole word`() {
        val span = selection.wordAt(centreOf("quick"))!!
        assertEquals("quick", selection.text(span))
    }

    @Test
    fun `a long press just off a word still finds it`() {
        val quick = boundsOf("quick")
        val span = selection.wordAt(NormalizedPoint(quick.left + 0.01f, quick.bottom + 0.005f))!!
        assertEquals("quick", selection.text(span))
    }

    @Test
    fun `a long press far from every word finds nothing`() {
        assertNull(selection.wordAt(NormalizedPoint(0.9f, 0.9f)))
    }

    // ------------------------------------------------------------------ carets: characters

    @Test
    fun `a handle lands between characters, inside a word`() {
        // "quick" starts at x 0.13: q 0.13-0.15, u 0.15-0.17, i 0.17-0.19. Just left of the u|i
        // boundary is still the boundary, the nearest one.
        val caret = selection.caretAt(NormalizedPoint(0.168f, 0.12f))!!
        assertEquals("The qu", selection.text.substring(0, caret))
    }

    @Test
    fun `a handle between two words is at the nearer word's edge`() {
        // "The" ends at 0.11, "quick" starts at 0.13.
        assertEquals(3, selection.caretAt(NormalizedPoint(0.115f, 0.12f)))
        assertEquals(4, selection.caretAt(NormalizedPoint(0.126f, 0.12f)))
    }

    @Test
    fun `a handle past the end of a line is at its end`() {
        val caret = selection.caretAt(NormalizedPoint(0.95f, 0.18f))!!
        assertEquals("fox jumps", selection.text.substring(caret - "fox jumps".length, caret))
        assertEquals('\n', selection.text[caret])
    }

    @Test
    fun `a handle above or below the text is on the first or last line`() {
        assertEquals(0, selection.caretAt(NormalizedPoint(0f, 0f)))
        assertEquals(selection.text.length, selection.caretAt(NormalizedPoint(0.9f, 0.99f)))
    }

    // ------------------------------------------------------------------ copied text

    @Test
    fun `a selection can start and end inside words`() {
        val start = selection.text.indexOf("uick")
        val end = selection.text.indexOf("ps") // "jum|ps"
        assertEquals("uick brown\nfox jum", selection.text(TextSpan(start, end)))
    }

    @Test
    fun `copied text keeps a blank line between paragraphs`() {
        val start = selection.text.indexOf("lazy")
        assertEquals("lazy\n\nDog.", selection.text(TextSpan(start, selection.text.length)))
    }

    @Test
    fun `all covers every word on the page`() {
        assertEquals(selection.text, selection.text(selection.all()!!))
    }

    // ------------------------------------------------------------------ highlights and handles

    @Test
    fun `the highlight runs from the first selected character to the last, one box a line`() {
        val start = selection.text.indexOf("uick")
        val end = selection.text.indexOf("ps")
        val rects = selection.highlightRects(TextSpan(start, end))

        assertEquals(2, rects.size)
        // From the u of "quick" to the end of "brown".
        assertClose(0.15f, rects[0].left)
        assertClose(boundsOf("brown").right, rects[0].right)
        // From the start of "fox" to the m of "jumps".
        assertClose(boundsOf("fox").left, rects[1].left)
        assertClose(boundsOf("jumps").left + 3 * CharWidth, rects[1].right)
    }

    @Test
    fun `an empty span highlights nothing`() {
        assertTrue(selection.highlightRects(TextSpan(5, 5)).isEmpty())
    }

    @Test
    fun `handles sit at the leading edge of the first character and the trailing edge of the last`() {
        val start = selection.text.indexOf("uick")
        val end = selection.text.indexOf("ps")

        assertEquals(NormalizedPoint(0.15f, 0.14f), selection.startHandle(start)!!.rounded())
        assertEquals(
            NormalizedPoint(boundsOf("jumps").left + 3 * CharWidth, 0.20f).rounded(),
            selection.endHandle(end)!!.rounded(),
        )
    }

    @Test
    fun `a handle on a space goes to the character beside it`() {
        // Caret 3 is after "The", before the space: the end handle belongs after "e"; a start
        // there belongs before the "q" of "quick".
        assertClose(boundsOf("The").right, selection.endHandle(3)!!.x)
        assertClose(boundsOf("quick").left, selection.startHandle(3)!!.x)
    }

    // ------------------------------------------------------------------ glyphs

    @Test
    fun `measured glyphs are used where the provider gives them`() {
        // A proportional word: a wide "m", then a narrow "il".
        val word =
            TextWord(
                "mil",
                NormalizedRect(0.1f, 0.1f, 0.2f, 0.14f),
                glyphs =
                    listOf(
                        NormalizedRect(0.10f, 0.1f, 0.16f, 0.14f),
                        NormalizedRect(0.16f, 0.1f, 0.18f, 0.14f),
                        NormalizedRect(0.18f, 0.1f, 0.20f, 0.14f),
                    ),
            )
        val measured =
            TextSelection(PageText(listOf(TextLine(listOf(word))), ContentOrigin.EMBEDDED))

        // Evenly shared, 0.155 would be past the m (0.133); measured, it is still inside it.
        assertEquals(1, measured.caretAt(NormalizedPoint(0.155f, 0.12f)))
        assertClose(0.16f, measured.endHandle(1)!!.x)
    }

    /** Ink boxes follow the letters: an "o" is shorter than a "d". The highlight must not. */
    @Test
    fun `a highlight is as tall as its line, whatever the letters`() {
        val word =
            TextWord(
                "od",
                NormalizedRect(0.1f, 0.10f, 0.14f, 0.14f),
                glyphs =
                    listOf(
                        NormalizedRect(0.10f, 0.12f, 0.12f, 0.14f), // o: x-height only
                        NormalizedRect(0.12f, 0.10f, 0.14f, 0.14f), // d: with its ascender
                    ),
            )
        val letters =
            TextSelection(PageText(listOf(TextLine(listOf(word))), ContentOrigin.EMBEDDED))

        val rect = letters.highlightRects(TextSpan(0, 1)).single()

        assertClose(0.10f, rect.top)
        assertClose(0.14f, rect.bottom)
    }

    @Test
    fun `a page with no words has nothing to select`() {
        val blank = TextSelection(PageText(listOf(TextLine(emptyList())), ContentOrigin.EMBEDDED))

        assertNull(blank.all())
        assertNull(blank.wordAt(NormalizedPoint(0.5f, 0.5f)))
        assertNull(blank.caretAt(NormalizedPoint(0.5f, 0.5f)))
    }

    // ------------------------------------------------------------------ right to left

    @Test
    fun `a right-to-left line is read, copied and handled in reading order`() {
        // Reading order: אחת, שתיים, laid out from the right edge leftwards, evenly shared.
        val rtl =
            TextSelection(
                PageText(
                    lines =
                        listOf(
                            TextLine(
                                listOf(
                                    TextWord("אחת", NormalizedRect(0.70f, 0.1f, 0.76f, 0.14f)),
                                    TextWord("שתיים", NormalizedRect(0.58f, 0.1f, 0.68f, 0.14f)),
                                )
                            )
                        ),
                    origin = ContentOrigin.EMBEDDED,
                )
            )

        assertEquals("אחת שתיים", rtl.text)
        // The rightmost edge is where reading starts.
        assertEquals(0, rtl.caretAt(NormalizedPoint(0.80f, 0.12f)))
        // The leftmost edge is where it ends.
        assertEquals(rtl.text.length, rtl.caretAt(NormalizedPoint(0.50f, 0.12f)))
        // One character in from the right: after א.
        assertEquals(1, rtl.caretAt(NormalizedPoint(0.74f, 0.12f)))
        // A start handle at the right edge of its first character.
        assertClose(0.76f, rtl.startHandle(0)!!.x)
    }

    // ------------------------------------------------------------------ margins and columns

    /**
     * An invented page shaped like a real invoice: a margin note set vertically, reading upwards,
     * delivered as a single line that spans the body's height; a label column; and a line that runs
     * from a value on to an address a column away.
     * - margin, x 0.03..0.04: "ACME" (y 0.76..0.80) and "SA" (y 0.72..0.74), read bottom to top;
     * - "Invoice:" at x 0.10..0.26, y 0.72..0.74;
     * - "MC123" at x 0.30..0.40, then "Main" a column away at x 0.60..0.68, y 0.72..0.74;
     * - "Date:" at x 0.10..0.20, y 0.76..0.78.
     */
    private val invoice =
        TextSelection(
            PageText(
                lines =
                    listOf(
                        TextLine(
                            listOf(upwards("ACME", bottom = 0.80f), upwards("SA", bottom = 0.74f))
                        ),
                        TextLine(listOf(across("Invoice:", left = 0.10f, top = 0.72f))),
                        TextLine(
                            listOf(
                                across("MC123", left = 0.30f, top = 0.72f),
                                across("Main", left = 0.60f, top = 0.72f),
                            )
                        ),
                        TextLine(listOf(across("Date:", left = 0.10f, top = 0.76f))),
                    ),
                origin = ContentOrigin.EMBEDDED,
            )
        )

    @Test
    fun `margin text does not capture a finger on the body`() {
        // At the height the margin note also covers, over the value column.
        val caret = invoice.caretAt(NormalizedPoint(0.34f, 0.73f))!!
        assertEquals("MC", invoice.text.substring(caret - 2, caret))
    }

    @Test
    fun `a finger on the margin moves along it, upwards`() {
        // "ACME" from y 0.80 up to 0.76: A at the bottom. 0.782 is nearest the C|M boundary.
        val caret = invoice.caretAt(NormalizedPoint(0.035f, 0.782f))!!
        assertEquals("AC", invoice.text.substring(caret - 2, caret))
    }

    @Test
    fun `a line that runs on into another column is two runs`() {
        val start = invoice.text.indexOf("MC123")
        val end = invoice.text.indexOf("Main") + 4

        val rects = invoice.highlightRects(TextSpan(start, end))

        // Not one box across the gap between the columns.
        assertEquals(2, rects.size)
        assertClose(0.40f, rects[0].right)
        assertClose(0.60f, rects[1].left)
    }

    @Test
    fun `in the gap between columns the nearer column wins`() {
        val caret = invoice.caretAt(NormalizedPoint(0.45f, 0.73f))!!
        assertEquals(invoice.text.indexOf("MC123") + 5, caret)
    }

    @Test
    fun `vertical text is highlighted along its height, as wide as its run`() {
        val start = invoice.text.indexOf("ACME")

        val rect = invoice.highlightRects(TextSpan(start, start + 2)).single() // "AC"

        assertClose(0.78f, rect.top)
        assertClose(0.80f, rect.bottom)
        assertClose(0.03f, rect.left)
        assertClose(0.04f, rect.right)
    }

    @Test
    fun `a one-letter word reads as the words beside it`() {
        // "A" alone says nothing; its line reads upwards, so it does too: its box is not split
        // along x, and a finger below it is before it.
        val line =
            TextSelection(
                PageText(
                    listOf(
                        TextLine(
                            listOf(upwards("ACME", bottom = 0.80f), upwards("A", bottom = 0.75f))
                        )
                    ),
                    ContentOrigin.EMBEDDED,
                )
            )
        assertEquals(line.text.indexOf("A", 1), line.caretAt(NormalizedPoint(0.035f, 0.749f)))
    }

    // ------------------------------------------------------------------ helpers

    /** A word set vertically, reading upwards from [bottom], 0.01 a character, at x 0.03..0.04. */
    private fun upwards(text: String, bottom: Float): TextWord {
        val glyphs =
            text.indices.map { i ->
                NormalizedRect(0.03f, bottom - (i + 1) * 0.01f, 0.04f, bottom - i * 0.01f)
            }
        return TextWord(text, glyphs.reduce(NormalizedRect::union), glyphs)
    }

    /** A word across the page from [left], 0.02 a character, 0.02 tall. */
    private fun across(text: String, left: Float, top: Float) =
        TextWord(text, NormalizedRect(left, top, left + text.length * 0.02f, top + 0.02f))

    private fun boundsOf(word: String) = selection.words.first { it.text == word }.bounds

    private fun centreOf(word: String): NormalizedPoint {
        val b = boundsOf(word)
        return NormalizedPoint((b.left + b.right) / 2, (b.top + b.bottom) / 2)
    }

    private fun assertClose(expected: Float, actual: Float) =
        assertEquals(expected, actual, 0.0001f)

    private fun NormalizedPoint.rounded() =
        NormalizedPoint(Math.round(x * 10_000) / 10_000f, Math.round(y * 10_000) / 10_000f)

    private companion object {
        const val CharWidth = 0.02f
        const val LineHeight = 0.04f

        /** Words laid out from x 0.05, monospaced, one character-width between them. */
        fun line(top: Float, vararg texts: String): TextLine {
            var left = 0.05f
            return TextLine(
                texts.map { text ->
                    val word =
                        TextWord(
                            text,
                            NormalizedRect(
                                left,
                                top,
                                left + text.length * CharWidth,
                                top + LineHeight,
                            ),
                        )
                    left += (text.length + 1) * CharWidth
                    word
                }
            )
        }
    }
}
