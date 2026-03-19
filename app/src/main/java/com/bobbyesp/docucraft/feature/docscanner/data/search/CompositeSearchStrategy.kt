package com.bobbyesp.docucraft.feature.docscanner.data.search

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.search.QuerySearchStrategy

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