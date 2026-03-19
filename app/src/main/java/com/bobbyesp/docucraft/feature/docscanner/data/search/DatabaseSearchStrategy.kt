package com.bobbyesp.docucraft.feature.docscanner.data.search

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.search.QuerySearchStrategy

class DatabaseSearchStrategy(
    private val repository: LocalDocumentsRepository
) : QuerySearchStrategy {
    override suspend fun search(query: String): List<ScannedDocument> =
        repository.searchDocuments(query)
}