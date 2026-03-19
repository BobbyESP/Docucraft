package com.bobbyesp.docucraft.feature.docscanner.domain.search

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument

/**
 * Strategy for performing a query-based search, typically against a database or remote API.
 */
interface QuerySearchStrategy {
    suspend fun search(query: String): List<ScannedDocument>
}