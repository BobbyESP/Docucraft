/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.search

import com.bobbyesp.docucraft.feature.docscanner.domain.search.TextRange

/**
 * A fragment of text as FTS4's `snippet()` returns it: one string, with a marker before and after
 * each stretch that matched. This takes the markers back out and says where the stretches are.
 *
 * The markers are control characters, which a page's text does not contain. Brackets or asterisks,
 * the usual choice, would be mistaken for the ones a document has of its own.
 */
internal object MarkedFragment {

    const val START = "\u0002"
    const val END = "\u0003"
    const val ELLIPSIS = "…"

    /** @return The fragment without its markers, and the stretches that were between them. */
    fun parse(marked: String): Pair<String, List<TextRange>> {
        val text = StringBuilder(marked.length)
        val highlights = ArrayList<TextRange>()
        var openedAt = -1

        for (char in marked) {
            when (char) {
                START[0] -> openedAt = text.length
                END[0] -> {
                    // An end with no start, or around nothing, marks nothing.
                    if (openedAt in 0 until text.length) {
                        highlights += TextRange(openedAt, text.length)
                    }
                    openedAt = -1
                }
                else -> text.append(char)
            }
        }
        return text.toString() to highlights
    }
}
