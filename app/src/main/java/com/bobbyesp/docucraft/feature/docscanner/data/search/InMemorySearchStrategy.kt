package com.bobbyesp.docucraft.feature.docscanner.data.search

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.search.LocalSearchStrategy

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