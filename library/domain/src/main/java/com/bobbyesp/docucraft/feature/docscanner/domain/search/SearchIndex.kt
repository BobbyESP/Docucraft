/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.search

/**
 * Finds the documents of the library that a text is about, in what they are called and described
 * as, and in what their pages say.
 *
 * A port, so that how the search is done is one binding: today SQLite's FTS4 with Room; substring
 * or CJK search would be another implementation, with nothing above it changing.
 *
 * What every implementation promises:
 * - **Only the library.** Not the bin, and not other apps' documents.
 * - **Every word has to match**, each as the beginning of a word, whatever its case or accents:
 *   "cancion" finds "Canción".
 * - **Best first.** A match in a title outranks one in a description, and either outranks one in
 *   the text of a page. Between equals, the document used most recently comes first.
 */
interface SearchIndex {

    /**
     * @param query Free text, as the user typed it. Nothing in it is an operator.
     * @return The matching documents, best first. Empty when nothing matches, and when [query] has
     *   nothing to search for: no letter and no digit.
     */
    suspend fun search(query: String): List<SearchHit>
}

/**
 * A document that matched.
 *
 * @property score How well, to order hits by. Only comparable between hits of the same search.
 * @property passage Where in the document's text the match is, when it is there and not only in
 *   what the document is called.
 */
data class SearchHit(val documentUuid: String, val score: Double, val passage: SearchPassage?)

/**
 * The words around a match in the text of a page, so the reader can tell why a document was found
 * before opening it.
 *
 * @property pageIndex Zero-based.
 * @property text A fragment of the page's text, with an ellipsis where it was cut.
 * @property highlights The parts of [text] that matched.
 */
data class SearchPassage(val pageIndex: Int, val text: String, val highlights: List<TextRange>)

/** A stretch of a text: from [start], up to but not including [end]. */
data class TextRange(val start: Int, val end: Int)
