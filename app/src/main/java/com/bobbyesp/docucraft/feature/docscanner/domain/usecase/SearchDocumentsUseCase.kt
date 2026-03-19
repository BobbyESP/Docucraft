package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.search.LocalSearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.domain.search.QuerySearchStrategy

/**
 * Use case for searching PDFs by title or description.
 * It coordinates different search strategies, typically trying a [QuerySearchStrategy] 
 * (like Database FTS or Remote API) first, and falling back to a [LocalSearchStrategy] 
 * (in-memory filtering) if needed.
 */
class SearchDocumentsUseCase(
    private val queryStrategy: QuerySearchStrategy,
    private val localStrategy: LocalSearchStrategy
) {
    suspend operator fun invoke(
        documents: List<ScannedDocument>,
        query: String
    ): List<ScannedDocument> {
        if (query.isBlank()) return documents

        return runCatching {
            val queryResults = queryStrategy.search(query)
            
            // If the query strategy returns results, we filter the 'live' document list by ID
            // to ensure we maintain the most up-to-date document state.
            if (queryResults.isEmpty()) throw NoSuchElementException("No results found in query strategies")

            val ids = queryResults.map { it.id }.toSet()
            documents.filter { it.id in ids }
        }.getOrElse {
            // Fallback to local in-memory search if query strategy fails or returns nothing
            localStrategy.search(documents, query)
        }
    }
}
