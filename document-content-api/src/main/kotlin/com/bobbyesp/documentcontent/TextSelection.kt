/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
 * **Text and order.** The page's text is laid out as it will be pasted ([text]): the words of a
 * line separated by a space, lines by a line break, a blank line kept where it separates
 * paragraphs. Selections are spans of that text, so what is copied is exactly what is between the
 * two handles, in the order the provider gives: the document's own reading order. A table stored
 * column by column is copied column by column, as the reference viewers do.
 *
 * **Geometry.** A provider's line is not always a strip of the page: it can run on from one column
 * to a word in another, and text set along the margin comes as a single line as tall as the page.
 * So where a finger is, and what to highlight, are worked out on *runs*: words of one line that sit
 * next to each other and read the same way, left to right, right to left, top to bottom or bottom
 * to top. A finger picks the run closest across its direction, and within it the nearest boundary
 * between characters along it.
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

    /** Which way each word reads. */
    private val flow: List<Flow>

    /** Each word's character boxes, one per character of its text, in reading order. */
    private val glyphs: List<List<NormalizedRect>>

    /** Runs of words that sit together, in reading order; see the class documentation. */
    private val runs: List<Run>

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
        flow = flows()
        glyphs = words.indices.map { words[it].glyphBoxes(flow[it]) }
        runs = runs()
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
     * The caret a dragged handle is at, wherever the finger is: in the run closest across its own
     * direction (for ordinary lines, the one at the finger's height), the nearest boundary between
     * characters along it. When several runs are as close, as the columns of a table row are, the
     * one nearest along its direction wins. Between two words it is the edge of the nearer one;
     * past the end of a line, that line's end. `null` only on a page with no words.
     */
    fun caretAt(point: NormalizedPoint): Int? {
        if (runs.isEmpty()) return null
        val closest = runs.minOf { it.across(point) }
        val run =
            runs.filter { it.across(point) <= closest + RunTieTolerance }.minBy { it.along(point) }
        val position = if (run.flow.vertical) point.y else point.x
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (word in run.words) {
            val boxes = glyphs[word]
            if (boxes.isEmpty()) continue
            for (boundary in 0..boxes.size) {
                val distance = abs(boundaryAt(word, boundary) - position)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = wordStart[word] + boundary
                }
            }
        }
        return best.takeIf { it >= 0 }
    }

    /** The text of [span], as it should be pasted. */
    fun text(span: TextSpan): String = text.substring(span.start, span.end)

    /** Every word on the page; `null` when there are none. */
    fun all(): TextSpan? =
        if (words.isEmpty()) null else TextSpan(wordStart[0], spanOf(words.lastIndex).end)

    /**
     * What to highlight for [span]: one rectangle per run, from its first selected character to its
     * last, gaps between words included, and as thick as the run: glyph boxes follow the ink, so an
     * "o" is shorter than a "d", and a highlight that followed them would jump about.
     */
    fun highlightRects(span: TextSpan): List<NormalizedRect> {
        if (span.isEmpty) return emptyList()
        return runs.mapNotNull { run ->
            var selected: NormalizedRect? = null
            for (word in run.words) {
                val from = maxOf(span.start, wordStart[word]) - wordStart[word]
                val to =
                    minOf(span.end, wordStart[word] + words[word].text.length) - wordStart[word]
                for (char in from until to) {
                    val box = glyphs[word][char]
                    selected = selected?.union(box) ?: box
                }
            }
            selected?.let {
                if (run.flow.vertical) it.copy(left = run.bounds.left, right = run.bounds.right)
                else it.copy(top = run.bounds.top, bottom = run.bounds.bottom)
            }
        }
    }

    /**
     * Where the handle for the start of a selection at [caret] goes: at the leading edge of the
     * first character from there on, below its run. `null` when no character follows.
     */
    fun startHandle(caret: Int): NormalizedPoint? {
        val (word, char) = characterAtOrAfter(caret) ?: return null
        return handleAt(word, flow[word].leading(glyphs[word][char]))
    }

    /**
     * Where the handle for the end of a selection at [caret] goes: at the trailing edge of the last
     * character before it, below its run. `null` when no character precedes.
     */
    fun endHandle(caret: Int): NormalizedPoint? {
        val (word, char) = characterBefore(caret) ?: return null
        return handleAt(word, flow[word].trailing(glyphs[word][char]))
    }

    /**
     * A handle at [edge] along [word]'s run: below it for a line, and beside it, on the side a
     * handle hangs, for text set vertically.
     */
    private fun handleAt(word: Int, edge: Float): NormalizedPoint {
        val run = runs.first { word in it.words }
        return if (run.flow.vertical) NormalizedPoint(run.bounds.right, edge)
        else NormalizedPoint(edge, run.bounds.bottom)
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
     * Where along its run the boundary before character [boundary] of [word] is: the leading edge
     * of the first character, the trailing edge of the last, and midway between two characters
     * otherwise.
     */
    private fun boundaryAt(word: Int, boundary: Int): Float {
        val boxes = glyphs[word]
        val direction = flow[word]
        return when (boundary) {
            0 -> direction.leading(boxes.first())
            boxes.size -> direction.trailing(boxes.last())
            else ->
                (direction.trailing(boxes[boundary - 1]) + direction.leading(boxes[boundary])) / 2f
        }
    }

    /**
     * Which way each word reads: from its glyphs when it has several; otherwise as the words beside
     * it on its line, since a one-letter word says nothing on its own; failing that, from its
     * letters and its shape.
     */
    private fun flows(): List<Flow> {
        val measured = words.map { it.measuredFlow() }
        return words.indices.map { word ->
            measured[word]
                ?: neighbours(word).firstNotNullOfOrNull { measured[it] }
                ?: words[word].guessedFlow()
        }
    }

    /** The other words of [word]'s line, nearest first. */
    private fun neighbours(word: Int): List<Int> {
        val line = lineOfWord[word]
        return words.indices
            .filter { it != word && lineOfWord[it] == line }
            .sortedBy { abs(it - word) }
    }

    /**
     * The page's runs: each line cut wherever the next word reads another way, or does not sit next
     * to the one before (a column away, or on another row).
     */
    private fun runs(): List<Run> {
        val found = mutableListOf<Run>()
        var first = 0
        for (word in 1..words.size) {
            val ends =
                word == words.size ||
                    lineOfWord[word] != lineOfWord[word - 1] ||
                    !continues(word - 1, word)
            if (ends) {
                val range = first until word
                found +=
                    Run(
                        words = range,
                        bounds = range.map { words[it].bounds }.reduce(NormalizedRect::union),
                        flow = flow[first],
                    )
                first = word
            }
        }
        return if (words.isEmpty()) emptyList() else found
    }

    /** Whether [next] carries on the run of [previous]: same direction, beside it, not far off. */
    private fun continues(previous: Int, next: Int): Boolean {
        if (flow[previous].vertical != flow[next].vertical) return false
        val a = words[previous].bounds
        val b = words[next].bounds
        return if (flow[previous].vertical) {
            val overlap = min(a.right, b.right) - max(a.left, b.left)
            val gap = max(a.top, b.top) - min(a.bottom, b.bottom)
            overlap >= RunOverlap * min(a.width, b.width) && gap <= RunGap * max(a.width, b.width)
        } else {
            val overlap = min(a.bottom, b.bottom) - max(a.top, b.top)
            val gap = max(a.left, b.left) - min(a.right, b.right)
            overlap >= RunOverlap * min(a.height, b.height) &&
                gap <= RunGap * max(a.height, b.height)
        }
    }

    /** Words of one line that sit together and read the same way. */
    private class Run(val words: IntRange, val bounds: NormalizedRect, val flow: Flow) {
        /** How far [point] is across the run's direction: for a line, above or below it. */
        fun across(point: NormalizedPoint): Float =
            if (flow.vertical) bounds.horizontalDistanceTo(point.x)
            else bounds.verticalDistanceTo(point.y)

        /** How far [point] is along the run's direction: for a line, before or after it. */
        fun along(point: NormalizedPoint): Float =
            if (flow.vertical) bounds.verticalDistanceTo(point.y)
            else bounds.horizontalDistanceTo(point.x)
    }

    companion object {
        /** About a fingertip's width off a word on a phone showing the page's full width. */
        const val DefaultTolerance: Float = 0.03f

        /**
         * How much closer across a run must be to win outright; within this, runs count as level
         * and the nearest along wins. Well under a line's spacing, so lines never tie.
         */
        private const val RunTieTolerance = 0.004f

        /**
         * How much two words must share across their direction to be on one run, of the smaller.
         */
        private const val RunOverlap = 0.3f

        /** How wide a gap may be, in text heights, before the next word is a column away. */
        private const val RunGap = 2f
    }
}

/** Which way text reads. [vertical] text is set along the page's height, as in a margin note. */
private enum class Flow(val vertical: Boolean, private val reversed: Boolean) {
    LeftToRight(vertical = false, reversed = false),
    RightToLeft(vertical = false, reversed = true),
    TopToBottom(vertical = true, reversed = false),
    BottomToTop(vertical = true, reversed = true);

    /** Where a character starts along the text: its left edge in a left-to-right line. */
    fun leading(box: NormalizedRect): Float =
        when {
            vertical -> if (reversed) box.bottom else box.top
            else -> if (reversed) box.right else box.left
        }

    /** Where a character ends along the text. */
    fun trailing(box: NormalizedRect): Float =
        when {
            vertical -> if (reversed) box.top else box.bottom
            else -> if (reversed) box.left else box.right
        }
}

/** Which way the word reads from its glyphs, when it has at least two to tell by. */
private fun TextWord.measuredFlow(): Flow? {
    val boxes = glyphs?.takeIf { it.size == text.length && it.size >= 2 } ?: return null
    val dx = (boxes.last().left + boxes.last().right - boxes.first().left - boxes.first().right)
    val dy = (boxes.last().top + boxes.last().bottom - boxes.first().top - boxes.first().bottom)
    return when {
        abs(dy) > abs(dx) -> if (dy < 0) Flow.BottomToTop else Flow.TopToBottom
        dx < 0 -> Flow.RightToLeft
        else -> Flow.LeftToRight
    }
}

/**
 * Which way a word reads with nothing but itself to go by: along the page's height if it is much
 * taller than wide (margin text is most often turned to read upwards), else by its letters.
 */
private fun TextWord.guessedFlow(): Flow =
    when {
        text.length > 1 && bounds.height > VerticalShape * bounds.width -> Flow.BottomToTop
        text.isRightToLeft() -> Flow.RightToLeft
        else -> Flow.LeftToRight
    }

/** How much taller than wide a word must be to be taken as set vertically. */
private const val VerticalShape = 1.5f

/**
 * One box per character: the provider's, or the word's box shared evenly among its characters, in
 * the order they are read.
 */
private fun TextWord.glyphBoxes(flow: Flow): List<NormalizedRect> {
    glyphs
        ?.takeIf { it.size == text.length }
        ?.let {
            return it
        }
    val count = text.length
    if (count == 0) return emptyList()
    return List(count) { i ->
        if (flow.vertical) {
            val height = bounds.height / count
            val slot = if (flow == Flow.BottomToTop) count - 1 - i else i
            bounds.copy(top = bounds.top + slot * height, bottom = bounds.top + (slot + 1) * height)
        } else {
            val width = bounds.width / count
            val slot = if (flow == Flow.RightToLeft) count - 1 - i else i
            bounds.copy(left = bounds.left + slot * width, right = bounds.left + (slot + 1) * width)
        }
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
