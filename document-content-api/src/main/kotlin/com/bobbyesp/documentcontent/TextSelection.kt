/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent

import kotlin.math.abs

/**
 * A stretch of a page's [TextSelection.text], from caret [start] to caret [end]. A caret is a
 * position between two characters: `0` is before the first, `text.length` after the last. [end] is
 * not included, so an empty span has `start == end`.
 */
data class TextSpan(val start: Int, val end: Int) {
    init {
        require(start in 0..end) { "Not a text span: $start..$end" }
    }

    val isEmpty: Boolean
        get() = start == end
}

/**
 * Selecting text on one page, character by character: which word a long press lands on, which caret
 * a dragged handle is at, what to copy and what to highlight. Pure logic over a [PageText],
 * whichever provider made it, so text recognition will reuse it as it is.
 *
 * The page's text is laid out as it will be pasted ([text]): the words of a line separated by a
 * space, lines by a line break, a blank line kept where it separates paragraphs. Selections are
 * spans of that text, so what is copied is exactly what is between the two handles. Positions on
 * the page only decide which caret a finger is at; everything after that follows reading order,
 * which is what makes a backwards drag, a selection over several lines and a right-to-left line
 * come out in the order they are read.
 *
 * Characters are placed with the provider's glyph boxes ([TextWord.glyphs]) when it gives them, and
 * otherwise by sharing the word's box evenly among its characters, which is exact for a monospaced
 * font and close for others.
 *
 * Distances are in normalized page units, the same on both axes of the unit square, although the
 * page is not square: a tolerance is a little wider across a portrait page than down it.
 */
class TextSelection(private val page: PageText) {

    /** Every word on the page, in reading order. */
    val words: List<TextWord> = page.lines.flatMap { it.words }

    /** The page's text, as it is copied. Carets and [TextSpan]s index it. */
    val text: String

    /** Where each word of [words] starts in [text]. */
    private val wordStart: IntArray

    /** The line each word of [words] is on. */
    private val lineOfWord: IntArray

    /** Where each line's words start in [words]. */
    private val lineFirstWord: IntArray =
        page.lines.runningFold(0) { start, line -> start + line.words.size }.toIntArray()

    /** Each line's extent on the page, `null` for a blank line. */
    private val lineBounds: List<NormalizedRect?> =
        page.lines.map { line -> line.words.map { it.bounds }.reduceOrNull(NormalizedRect::union) }

    /** Each word's character boxes, one per character of its text. */
    private val glyphs: List<List<NormalizedRect>>

    init {
        val starts = IntArray(words.size)
        val lines = IntArray(words.size)
        val builder = StringBuilder()
        var index = 0
        page.lines.forEachIndexed { lineIndex, line ->
            if (lineIndex > 0) builder.append('\n')
            line.words.forEachIndexed { position, word ->
                if (position > 0) builder.append(' ')
                starts[index] = builder.length
                lines[index] = lineIndex
                builder.append(word.text)
                index++
            }
        }
        text = builder.toString()
        wordStart = starts
        lineOfWord = lines
        glyphs = words.map { it.glyphBoxes() }
    }

    /**
     * The word under [point], for a long press: the word it falls on or, failing that, the closest
     * word no further than [tolerance]. `null` when the finger is on nothing.
     */
    fun wordAt(point: NormalizedPoint, tolerance: Float = DefaultTolerance): TextSpan? {
        val hit = words.indexOfFirst { point in it.bounds }
        val word =
            if (hit >= 0) {
                hit
            } else {
                val closest =
                    words.indices.minByOrNull { words[it].bounds.distanceTo(point) } ?: return null
                closest.takeIf { words[it].bounds.distanceTo(point) <= tolerance } ?: return null
            }
        return spanOf(word)
    }

    /**
     * The caret a dragged handle is at, wherever the finger is: on the closest line, the closest
     * boundary between characters along it. Between two words it is the edge of the nearer one;
     * past the end of a line, that line's end; above or below the text, the first or last line.
     * `null` only on a page with no words.
     */
    fun caretAt(point: NormalizedPoint): Int? {
        val line =
            lineBounds.indices
                .filter { lineBounds[it] != null }
                .minByOrNull { lineBounds[it]!!.verticalDistanceTo(point.y) } ?: return null
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (word in lineFirstWord[line] until lineFirstWord[line + 1]) {
            val boxes = glyphs[word]
            if (boxes.isEmpty()) continue
            for (boundary in 0..boxes.size) {
                val distance = abs(boundaryX(word, boundary) - point.x)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = wordStart[word] + boundary
                }
            }
        }
        return best
    }

    /** The text of [span], as it should be pasted. */
    fun text(span: TextSpan): String = text.substring(span.start, span.end)

    /** Every word on the page; `null` when there are none. */
    fun all(): TextSpan? =
        if (words.isEmpty()) null else TextSpan(wordStart[0], spanOf(words.lastIndex).end)

    /**
     * What to highlight for [span]: one rectangle per line, from its first selected character to
     * its last, gaps between words included, and as tall as the line: glyph boxes follow the ink,
     * so an "o" is shorter than a "d", and a highlight that followed them would jump about.
     */
    fun highlightRects(span: TextSpan): List<NormalizedRect> {
        if (span.isEmpty) return emptyList()
        val byLine = sortedMapOf<Int, NormalizedRect>()
        for (word in words.indices) {
            val from = maxOf(span.start, wordStart[word]) - wordStart[word]
            val to = minOf(span.end, wordStart[word] + words[word].text.length) - wordStart[word]
            if (from >= to) continue
            val selected = (from until to).map { glyphs[word][it] }.reduce(NormalizedRect::union)
            byLine.merge(lineOfWord[word], selected, NormalizedRect::union)
        }
        return byLine.map { (line, rect) ->
            val height = lineBounds[line]!!
            rect.copy(top = height.top, bottom = height.bottom)
        }
    }

    /**
     * Where the handle for the start of a selection at [caret] goes: the bottom of the line, at the
     * leading edge of the first character from there on. `null` when no character follows.
     */
    fun startHandle(caret: Int): NormalizedPoint? {
        val (word, char) = characterAtOrAfter(caret) ?: return null
        val box = glyphs[word][char]
        val x = if (isRightToLeft(word)) box.right else box.left
        return NormalizedPoint(x, lineBounds[lineOfWord[word]]!!.bottom)
    }

    /**
     * Where the handle for the end of a selection at [caret] goes: the bottom of the line, at the
     * trailing edge of the last character before it. `null` when no character precedes.
     */
    fun endHandle(caret: Int): NormalizedPoint? {
        val (word, char) = characterBefore(caret) ?: return null
        val box = glyphs[word][char]
        val x = if (isRightToLeft(word)) box.left else box.right
        return NormalizedPoint(x, lineBounds[lineOfWord[word]]!!.bottom)
    }

    private fun spanOf(word: Int) =
        TextSpan(wordStart[word], wordStart[word] + words[word].text.length)

    /** The first character of a word at or after [caret], skipping spaces and line breaks. */
    private fun characterAtOrAfter(caret: Int): Pair<Int, Int>? {
        for (word in words.indices) {
            val end = wordStart[word] + words[word].text.length
            if (caret < end) return word to maxOf(0, caret - wordStart[word])
        }
        return null
    }

    /** The last character of a word before [caret], skipping spaces and line breaks. */
    private fun characterBefore(caret: Int): Pair<Int, Int>? {
        for (word in words.indices.reversed()) {
            if (caret > wordStart[word]) {
                val end = wordStart[word] + words[word].text.length
                return word to (minOf(caret, end) - wordStart[word] - 1)
            }
        }
        return null
    }

    /**
     * Where along the line the boundary before character [boundary] of [word] is: the leading edge
     * of the first character, the trailing edge of the last, and midway between two characters
     * otherwise.
     */
    private fun boundaryX(word: Int, boundary: Int): Float {
        val boxes = glyphs[word]
        val rtl = isRightToLeft(word)
        fun leading(box: NormalizedRect) = if (rtl) box.right else box.left
        fun trailing(box: NormalizedRect) = if (rtl) box.left else box.right
        return when (boundary) {
            0 -> leading(boxes.first())
            boxes.size -> trailing(boxes.last())
            else -> (trailing(boxes[boundary - 1]) + leading(boxes[boundary])) / 2f
        }
    }

    /**
     * Whether [word] runs right to left: from its glyphs when there are several, from its letters
     * otherwise.
     */
    private fun isRightToLeft(word: Int): Boolean {
        val boxes = glyphs[word]
        if (words[word].glyphs != null && boxes.size > 1) {
            return (boxes.last().left + boxes.last().right) <
                (boxes.first().left + boxes.first().right)
        }
        return words[word].text.isRightToLeft()
    }

    companion object {
        /** About a fingertip's width off a word on a phone showing the page's full width. */
        const val DefaultTolerance: Float = 0.03f
    }
}

/**
 * One box per character: the provider's, or the word's box shared evenly among its characters, in
 * reading order (right to left for a right-to-left word).
 */
private fun TextWord.glyphBoxes(): List<NormalizedRect> {
    glyphs
        ?.takeIf { it.size == text.length }
        ?.let {
            return it
        }
    val count = text.length.coerceAtLeast(1)
    val width = bounds.width / count
    val rtl = text.isRightToLeft()
    return List(text.length) { i ->
        val slot = if (rtl) count - 1 - i else i
        NormalizedRect(
            left = bounds.left + slot * width,
            top = bounds.top,
            right = bounds.left + (slot + 1) * width,
            bottom = bounds.bottom,
        )
    }
}

/** Whether the first letter with a direction is right to left, as in Hebrew or Arabic. */
private fun String.isRightToLeft(): Boolean {
    for (char in this) {
        when (Character.getDirectionality(char)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
        }
    }
    return false
}
