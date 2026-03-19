package com.bobbyesp.docucraft.feature.docscanner.domain.search

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument

/**
 * Strategy for performing a local, in-memory search on an existing list of documents.
 * This is often used as a fallback when the primary [QuerySearchStrategy] fails or returns no results.
 */
interface LocalSearchStrategy {
    fun search(
        documents: List<ScannedDocument>,
        query: String
    ): List<ScannedDocument>
}
