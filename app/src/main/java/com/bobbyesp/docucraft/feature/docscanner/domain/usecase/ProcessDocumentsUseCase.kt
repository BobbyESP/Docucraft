/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.search.LocalSearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.domain.search.QuerySearchStrategy

class ProcessDocumentsUseCase(
    private val querySearchStrategy: QuerySearchStrategy,
    private val localSearchStrategy: LocalSearchStrategy,
) {
    suspend operator fun invoke(
        documents: List<Document.Managed>,
        query: String,
        filter: FilterOptions,
        sort: SortOption,
    ): List<Document.Managed> {
        val searched = search(documents, query)
        val filtered = filter(searched, filter)
        return sort(filtered, sort)
    }

    private suspend fun search(
        documents: List<Document.Managed>,
        query: String,
    ): List<Document.Managed> {
        if (query.isBlank()) return documents

        return runCatching {
            val queryResults = querySearchStrategy.search(query)
            if (queryResults.isEmpty()) throw NoSuchElementException("No results found")

            val ids = queryResults.map { it.uuid }.toSet()
            documents.filter { it.uuid in ids }
        }
            .getOrElse { localSearchStrategy.search(documents, query) }
    }

    private fun filter(
        documents: List<Document.Managed>,
        filter: FilterOptions,
    ): List<Document.Managed> {
        return documents.filterByPages(filter).filterBySize(filter).filterByDate(filter)
    }

    private fun sort(documents: List<Document.Managed>, sort: SortOption): List<Document.Managed> {
        return when (sort.criteria) {
            SortOption.Criteria.DATE ->
                if (sort.order == SortOption.Order.DESC)
                    documents.sortedByDescending { it.createdAtEpochMillis }
                else documents.sortedBy { it.createdAtEpochMillis }

            SortOption.Criteria.NAME ->
                if (sort.order == SortOption.Order.DESC) documents.sortedByDescending { it.name }
                else documents.sortedBy { it.name }

            SortOption.Criteria.SIZE ->
                if (sort.order == SortOption.Order.DESC)
                    documents.sortedByDescending { it.sizeBytes ?: 0 }
                else documents.sortedBy { it.sizeBytes ?: 0 }
        }
    }

    private fun List<Document.Managed>.filterByPages(filter: FilterOptions) =
        // A document whose pages have not been counted cannot be said to have that many.
        filter.minPageCount?.let { min -> filter { (it.pageCount ?: 0) >= min } } ?: this

    private fun List<Document.Managed>.filterBySize(filter: FilterOptions) =
        filter.minFileSize?.let { min -> filter { (it.sizeBytes ?: 0) >= min } } ?: this

    private fun List<Document.Managed>.filterByDate(filter: FilterOptions) =
        filter.dateRange?.let { range -> filter { it.createdAtEpochMillis in range } } ?: this
}
