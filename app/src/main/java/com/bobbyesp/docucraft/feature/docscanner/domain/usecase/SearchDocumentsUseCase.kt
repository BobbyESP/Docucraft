/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.search.SearchIndex
import com.bobbyesp.docucraft.feature.docscanner.domain.search.SearchPassage

/**
 * A document found by a search, and where in its text, if that is where it was found.
 *
 * @property passage The fragment that matched and its page; `null` when the match is in what the
 *   document is called or described as.
 */
data class SearchResult(val document: Document.Managed, val passage: SearchPassage?)

/**
 * Searches the library, and answers with documents rather than with the index's hits.
 *
 * The index says which documents match and in what order; what each of them is comes from
 * [documents], the library as its screen is observing it. So a result is never staler than the
 * list, and a search repeated when the library changes follows a rename or a deletion without the
 * query being typed again.
 */
class SearchDocumentsUseCase(private val searchIndex: SearchIndex) {

    /**
     * @param documents The library to answer from.
     * @return What matches [query], best first. Nothing for a blank query.
     */
    suspend operator fun invoke(
        documents: List<Document.Managed>,
        query: String,
    ): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        val byUuid = documents.associateBy { it.uuid }
        return searchIndex.search(query).mapNotNull { hit ->
            // A hit for a document the library no longer lists was deleted between the two reads.
            byUuid[hit.documentUuid]?.let { SearchResult(document = it, passage = hit.passage) }
        }
    }
}
