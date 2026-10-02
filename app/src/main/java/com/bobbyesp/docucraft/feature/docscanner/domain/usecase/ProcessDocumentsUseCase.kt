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
        documents: List<Document>,
        query: String,
        filter: FilterOptions,
        sort: SortOption,
    ): List<Document> {
        val searched = search(documents, query)
        val filtered = filter(searched, filter)
        return sort(filtered, sort)
    }

    private suspend fun search(
        documents: List<Document>,
        query: String,
    ): List<Document> {
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
        documents: List<Document>,
        filter: FilterOptions,
    ): List<Document> {
        return documents.filterByPages(filter).filterBySize(filter).filterByDate(filter)
    }

    private fun sort(documents: List<Document>, sort: SortOption): List<Document> {
        return when (sort.criteria) {
            SortOption.Criteria.DATE ->
                if (sort.order == SortOption.Order.DESC)
                    documents.sortedByDescending { it.capturedAtEpochMillis }
                else documents.sortedBy { it.capturedAtEpochMillis }

            SortOption.Criteria.NAME ->
                if (sort.order == SortOption.Order.DESC)
                    documents.sortedByDescending { it.title ?: it.filename }
                else documents.sortedBy { it.title ?: it.filename }

            SortOption.Criteria.SIZE ->
                if (sort.order == SortOption.Order.DESC)
                    documents.sortedByDescending { it.sizeBytes }
                else documents.sortedBy { it.sizeBytes }
        }
    }

    private fun List<Document>.filterByPages(filter: FilterOptions) =
        filter.minPageCount?.let { min -> filter { it.pageCount >= min } } ?: this

    private fun List<Document>.filterBySize(filter: FilterOptions) =
        filter.minFileSize?.let { min -> filter { it.sizeBytes >= min } } ?: this

    private fun List<Document>.filterByDate(filter: FilterOptions) =
        filter.dateRange?.let { range -> filter { it.capturedAtEpochMillis in range } } ?: this
}
