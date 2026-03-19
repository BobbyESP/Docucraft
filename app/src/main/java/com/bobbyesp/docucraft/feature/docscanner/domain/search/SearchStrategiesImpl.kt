package com.bobbyesp.docucraft.feature.docscanner.domain.search

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository

class DatabaseSearchStrategy(
    private val repository: LocalDocumentsRepository
) : QuerySearchStrategy {
    override suspend fun search(query: String): List<ScannedDocument> =
        repository.searchDocuments(query)
}

class InMemorySearchStrategy : LocalSearchStrategy {
    override fun search(documents: List<ScannedDocument>, query: String): List<ScannedDocument> {
        if (query.isBlank()) return documents

        val lowerQuery = query.lowercase()

        return documents.filter { doc ->
            val titleMatch = doc.title?.lowercase()?.contains(lowerQuery) == true
            val descriptionMatch = doc.description?.lowercase()?.contains(lowerQuery) == true

            if (doc.title != null) {
                titleMatch || descriptionMatch
            } else {
                doc.filename.lowercase().contains(lowerQuery) || descriptionMatch
            }
        }
    }
}

class CompositeSearchStrategy(
    private val strategies: List<QuerySearchStrategy>
) : QuerySearchStrategy {
    override suspend fun search(query: String): List<ScannedDocument> {
        for (strategy in strategies) {
            val result = runCatching { strategy.search(query) }.getOrNull()
            if (!result.isNullOrEmpty()) return result
        }
        return emptyList()
    }
}
