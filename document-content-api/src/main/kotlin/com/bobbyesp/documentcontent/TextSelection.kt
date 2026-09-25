/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent

/**
 * A run of words on one page, by their position in [TextSelection.words]: reading order, not screen
 * order. Both ends are included, and [first] never comes after [last].
 */
data class WordRange(val first: Int, val last: Int) {
    init {
        require(first in 0..last) { "Not a word range: $first..$last" }
    }

    operator fun contains(index: Int): Boolean = index in first..last
}

/**
 * Selecting text on one page: which word is under a finger, which words lie between two, what to
 * copy and what to highlight. Pure logic over a [PageText], whichever provider made it, so text
 * recognition will reuse it as it is.
 *
 * Positions on the page decide only *which word* a finger is on. Everything after that follows
 * reading order: the words of [PageText.lines] as the provider lists them. That is what makes a
 * backwards drag, a selection over several lines and a right-to-left line come out in the order
 * they are read.
 *
 * Distances are in normalized page units, the same on both axes of the unit square, although the
 * page is not square: a tolerance is a little wider across a portrait page than down it.
 */
class TextSelection(private val page: PageText) {

    /** Every word on the page, in reading order. [WordRange] indexes this list. */
    val words: List<TextWord> = page.lines.flatMap { it.words }

    /** The line each word of [words] is on. */
    private val lineOfWord: IntArray =
        page.lines
            .flatMapIndexed { lineIndex, line -> List(line.words.size) { lineIndex } }
            .toIntArray()

    /** Where each line's words start in [words]. */
    private val lineStart: IntArray =
        page.lines.runningFold(0) { start, line -> start + line.words.size }.toIntArray()

    /** Each line's extent on the page, `null` for a blank line. */
    private val lineBounds: List<NormalizedRect?> =
        page.lines.map { line -> line.words.map { it.bounds }.reduceOrNull(NormalizedRect::union) }

    /**
     * The word under [point], for a long press: the word it falls on or, failing that, the closest
     * word no further than [tolerance]. `null` when the finger is on nothing.
     */
    fun wordAt(point: NormalizedPoint, tolerance: Float = DefaultTolerance): Int? {
        val hit = words.indexOfFirst { point in it.bounds }
        if (hit >= 0) return hit
        val closest =
            words.indices.minByOrNull { words[it].bounds.distanceTo(point) } ?: return null
        return closest.takeIf { words[it].bounds.distanceTo(point) <= tolerance }
    }

    /**
     * The word a dragged handle is at, wherever the finger is: on the closest line, the closest
     * word along it. Past the end of a line it is the line's last word on that side, and above or
     * below the text it is the first or last line. `null` only on a page with no words.
     */
    fun nearestWord(point: NormalizedPoint): Int? {
        val line =
            lineBounds.indices
                .filter { lineBounds[it] != null }
                .minByOrNull { lineBounds[it]!!.verticalDistanceTo(point.y) } ?: return null
        return (lineStart[line] until lineStart[line + 1]).minBy {
            words[it].bounds.horizontalDistanceTo(point.x)
        }
    }

    /** The words from [anchor] to [focus], in whichever order they were chosen. */
    fun range(anchor: Int, focus: Int): WordRange {
        require(anchor in words.indices && focus in words.indices) {
            "No such word: $anchor or $focus of ${words.size}"
        }
        return WordRange(minOf(anchor, focus), maxOf(anchor, focus))
    }

    /** Every word on the page; `null` when there are none. */
    fun all(): WordRange? = if (words.isEmpty()) null else WordRange(0, words.lastIndex)

    /**
     * The text of [range] as it should be pasted: words on a line separated by a space, lines by a
     * line break, and a blank line kept where it separates paragraphs.
     */
    fun text(range: WordRange): String = buildString {
        val firstLine = lineOfWord[range.first]
        val lastLine = lineOfWord[range.last]
        for (line in firstLine..lastLine) {
            if (line > firstLine) append('\n')
            val from = maxOf(lineStart[line], range.first)
            val to = minOf(lineStart[line + 1] - 1, range.last)
            (from..to).joinTo(this, separator = " ") { words[it].text }
        }
    }

    /**
     * What to highlight for [range]: one rectangle per line, covering the selected words on it and
     * the gaps between them, in reading order.
     */
    fun highlightRects(range: WordRange): List<NormalizedRect> =
        (range.first..range.last)
            .groupBy { lineOfWord[it] }
            .values
            .map { indices -> indices.map { words[it].bounds }.reduce(NormalizedRect::union) }

    companion object {
        /** About a fingertip's width off a word on a phone showing the page's full width. */
        const val DefaultTolerance: Float = 0.03f
    }
}
