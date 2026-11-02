/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.search

import com.bobbyesp.documentcontent.TextSpan
import java.util.TreeMap

/**
 * One match of a search.
 *
 * @property page the page it is on.
 * @property index which of that page's matches it is, from 0. Matches found on other pages do not
 *   move it.
 */
data class MatchRef(val page: Int, val index: Int)

/**
 * The matches a search has found so far, kept by page.
 *
 * Pages are read near the reader first, so matches arrive out of order; keeping them by page makes
 * "the third of twelve" mean the same thing throughout. Immutable: adding a page gives a new
 * instance, so the screen detects changes by reference. Holds all of the next/previous logic, so it
 * is tested without a device.
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

    /** These matches plus [spans], all of [page]'s. No spans returns this instance. */
    fun with(page: Int, spans: List<TextSpan>): SearchMatches {
        if (spans.isEmpty()) return this
        val next = TreeMap(byPage)
        next[page] = spans
        return SearchMatches(next)
    }

    /** The span [ref] points at, or `null` if it points at nothing. */
    fun spanOf(ref: MatchRef): TextSpan? = byPage[ref.page]?.getOrNull(ref.index)

    /** Position of [ref] among all matches, from 1; `0` if it points at nothing. */
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

    /** The first match at or after [offset] on [page], or on a later page; `null` if none. */
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

    /** The match after [ref], wrapping to the first after the last. */
    fun next(ref: MatchRef?): MatchRef? {
        if (isEmpty) return null
        if (ref == null || spanOf(ref) == null) return first()
        if (ref.index + 1 < on(ref.page).size) return MatchRef(ref.page, ref.index + 1)
        val following = byPage.higherEntry(ref.page) ?: return first()
        return MatchRef(following.key, 0)
    }

    /** The match before [ref], wrapping to the last before the first. */
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
