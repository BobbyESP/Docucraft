/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent

/**
 * A word in the document: its page, and its index in that page's [TextSelection.words]. Ordered by
 * page, then by reading order on the page.
 */
data class TextPosition(val page: Int, val word: Int) : Comparable<TextPosition> {
    override fun compareTo(other: TextPosition): Int =
        compareValuesBy(this, other, TextPosition::page, TextPosition::word)
}

/**
 * A selection that may run over several pages: from [start] to [end], both included, [start] never
 * after [end]. Every page between them is selected whole.
 *
 * Only the two ends are kept. Each page's share is worked out from its own [TextSelection] when it
 * is needed ([rangeOn]), so a selection over many pages costs nothing until it is drawn or copied,
 * and only the pages on screen have to be drawn.
 */
data class DocumentSelection(val start: TextPosition, val end: TextPosition) {
    init {
        require(start <= end) { "A selection cannot end before it starts: $start..$end" }
    }

    /** The pages the selection touches. */
    val pages: IntRange
        get() = start.page..end.page

    /**
     * The words selected on [page], which has [wordCount] words; `null` when the selection does not
     * reach it or it has no words.
     */
    fun rangeOn(page: Int, wordCount: Int): WordRange? {
        if (page !in pages || wordCount == 0) return null
        val first = if (page == start.page) start.word else 0
        val last = if (page == end.page) minOf(end.word, wordCount - 1) else wordCount - 1
        return if (first <= last) WordRange(first, last) else null
    }

    /**
     * The selected text as it should be pasted: each page's as [TextSelection.text] gives it, pages
     * separated by a line break.
     *
     * @param page The text of each page in [pages], or `null` for one with none (a scanned page),
     *   which is left out. Every page in [pages] is asked for: load them all before copying.
     */
    fun text(page: (index: Int) -> TextSelection?): String =
        pages
            .mapNotNull { index ->
                val selection = page(index) ?: return@mapNotNull null
                rangeOn(index, selection.words.size)?.let(selection::text)
            }
            .joinToString(separator = "\n")

    /** What to highlight on [page], whose text is [selection]. */
    fun highlightRects(page: Int, selection: TextSelection): List<NormalizedRect> =
        rangeOn(page, selection.words.size)?.let(selection::highlightRects).orEmpty()

    companion object {
        /** The selection from [anchor] to [focus], in whichever order they were chosen. */
        fun between(anchor: TextPosition, focus: TextPosition): DocumentSelection =
            DocumentSelection(minOf(anchor, focus), maxOf(anchor, focus))
    }
}
