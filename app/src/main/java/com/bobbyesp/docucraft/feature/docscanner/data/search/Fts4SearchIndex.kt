/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.search

import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.PageMatch
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.SearchDao
import com.bobbyesp.docucraft.feature.docscanner.domain.search.SearchHit
import com.bobbyesp.docucraft.feature.docscanner.domain.search.SearchIndex
import com.bobbyesp.docucraft.feature.docscanner.domain.search.SearchPassage
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Search with SQLite's FTS4, the full-text index Room supports and every device has.
 *
 * There are two indexes, and a search asks both: one over what a document is called and described
 * as, one over the text of its pages. A document's score is the score of its own row plus half that
 * of its best page, so a word in a title outranks the same word somewhere in the text, and a
 * document does not win by being long.
 *
 * FTS4 matches whole words and their beginnings. It does not find text inside a word, and it does
 * not split Chinese, Japanese or Korean into words; FTS5's trigram tokenizer would, and would be
 * another implementation of [SearchIndex].
 */
class Fts4SearchIndex(private val searchDao: SearchDao) : SearchIndex {

    override suspend fun search(query: String): List<SearchHit> {
        val expression = Fts4Query.of(query) ?: return emptyList()

        val found = HashMap<String, Found>()

        for (match in searchDao.matchDocuments(expression)) {
            found[match.uuid] =
                Found(
                    lastActivityAt = match.lastActivityAt,
                    documentScore = Bm25.score(match.matchInfo.asMatchInfo(), DOCUMENT_WEIGHTS),
                )
        }

        val pages =
            searchDao.matchPages(
                query = expression,
                start = MarkedFragment.START,
                end = MarkedFragment.END,
                ellipsis = MarkedFragment.ELLIPSIS,
                words = FRAGMENT_WORDS,
            )
        for (page in pages) {
            val score = Bm25.score(page.matchInfo.asMatchInfo(), PAGE_WEIGHTS)
            val document = found.getOrPut(page.uuid) { Found(lastActivityAt = page.lastActivityAt) }
            if (score > document.bestPageScore) {
                document.bestPageScore = score
                document.bestPage = page
            }
        }

        return found.entries
            .map { (uuid, document) ->
                Ranked(
                    lastActivityAt = document.lastActivityAt,
                    hit =
                        SearchHit(
                            documentUuid = uuid,
                            score = document.documentScore + PAGE_SHARE * document.bestPageScore,
                            passage = document.bestPage?.toPassage(),
                        ),
                )
            }
            .sortedWith(
                compareByDescending<Ranked> { it.hit.score }.thenByDescending { it.lastActivityAt }
            )
            .map { it.hit }
    }

    private fun PageMatch.toPassage(): SearchPassage {
        val (text, highlights) = MarkedFragment.parse(fragment)
        return SearchPassage(pageIndex = pageIndex, text = text, highlights = highlights)
    }

    /** `matchinfo` is a blob of 32-bit integers in the byte order of the machine it ran on. */
    private fun ByteArray.asMatchInfo(): IntArray {
        val integers = ByteBuffer.wrap(this).order(ByteOrder.nativeOrder()).asIntBuffer()
        return IntArray(integers.remaining()).also { integers.get(it) }
    }

    /** What is known of a document while the two result sets are put together. */
    private class Found(val lastActivityAt: Long, val documentScore: Double = 0.0) {
        var bestPageScore = 0.0
        var bestPage: PageMatch? = null
    }

    private class Ranked(val lastActivityAt: Long, val hit: SearchHit)

    private companion object {
        /**
         * How much a match counts in each column of `documents_fts`, in the order
         * `DocumentFtsEntity` declares them: title, original name, suggested title, description,
         * and the PDF's author, subject and keywords.
         */
        val DOCUMENT_WEIGHTS = doubleArrayOf(10.0, 8.0, 6.0, 4.0, 2.0, 2.0, 2.0)

        /** `page_texts_fts` has one column, the text. */
        val PAGE_WEIGHTS = doubleArrayOf(1.0)

        /** How much of its best page's score a document gets. */
        const val PAGE_SHARE = 0.5

        /** About how many words of a page a result shows around the match. */
        const val FRAGMENT_WORDS = 12
    }
}
