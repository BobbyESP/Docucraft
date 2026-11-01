/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.search

import com.bobbyesp.documentcontent.TextSpan
import java.util.TreeMap

/**
 * One match of a search: the page it is on, and which of that page's matches it is, counting from
 * the top. Stable for as long as the matches it points into are: finding more elsewhere does not
 * move it.
 */
data class MatchRef(val page: Int, val index: Int)

/**
 * What a search has found so far, in the document's reading order whatever order the pages were
 * read in.
 *
 * Pages are read near the reader first and the rest after, so matches arrive out of order; keeping
 * them by page makes "the third of twelve" mean the same thing the whole time. Immutable: adding a
 * page's matches gives a new set, so the screen can tell that something changed by looking at the
 * reference.
 *
 * Plain Kotlin, and the whole of the "which match comes next" logic, so that it is tested without a
 * device.
 */
class SearchMatches private constructor(private val byPage: TreeMap<Int, List<TextSpan>>) {

    /** How many matches there are, over every page found so far. */
    val total: Int = byPage.values.sumOf { it.size }

    val isEmpty: Boolean
        get() = total == 0

    /** The matches on [page], in reading order. */
    fun on(page: Int): List<TextSpan> = byPage[page].orEmpty()

    /** The pages that have a match. */
    val pages: Set<Int>
        get() = byPage.keys

    /** These matches and [spans], which are all of [page]'s. A page with none changes nothing. */
    fun with(page: Int, spans: List<TextSpan>): SearchMatches {
        if (spans.isEmpty()) return this
        val next = TreeMap(byPage)
        next[page] = spans
        return SearchMatches(next)
    }

    /** The span [ref] points at; `null` if it points at nothing, as after the matches changed. */
    fun spanOf(ref: MatchRef): TextSpan? = byPage[ref.page]?.getOrNull(ref.index)

    /** Which match of the whole document [ref] is, counting from 1; `0` if it is none. */
    fun numberOf(ref: MatchRef): Int {
        if (spanOf(ref) == null) return 0
        var before = 0
        for ((page, spans) in byPage) {
            if (page >= ref.page) break
            before += spans.size
        }
        return before + ref.index + 1
    }

    /** The first match of the document. */
    fun first(): MatchRef? = byPage.firstEntry()?.let { MatchRef(it.key, 0) }

    /**
     * The first match that starts at or after [offset] on [page], or on any later page. `null` if
     * there is none: everything found so far is before.
     */
    fun firstFrom(page: Int, offset: Int): MatchRef? {
        byPage[page]
            ?.indexOfFirst { it.start >= offset }
            ?.takeIf { it >= 0 }
            ?.let {
                return MatchRef(page, it)
            }
        val later = byPage.higherEntry(page) ?: return null
        return MatchRef(later.key, 0)
    }

    /** The match after [ref], and the first again after the last. */
    fun next(ref: MatchRef?): MatchRef? {
        if (isEmpty) return null
        if (ref == null || spanOf(ref) == null) return first()
        if (ref.index + 1 < on(ref.page).size) return MatchRef(ref.page, ref.index + 1)
        val following = byPage.higherEntry(ref.page) ?: return first()
        return MatchRef(following.key, 0)
    }

    /** The match before [ref], and the last again before the first. */
    fun previous(ref: MatchRef?): MatchRef? {
        if (isEmpty) return null
        val last = byPage.lastEntry()
        if (ref == null || spanOf(ref) == null) return MatchRef(last.key, last.value.lastIndex)
        if (ref.index > 0) return MatchRef(ref.page, ref.index - 1)
        val preceding =
            byPage.lowerEntry(ref.page) ?: return MatchRef(last.key, last.value.lastIndex)
        return MatchRef(preceding.key, preceding.value.lastIndex)
    }

    companion object {
        val None = SearchMatches(TreeMap())
    }
}
