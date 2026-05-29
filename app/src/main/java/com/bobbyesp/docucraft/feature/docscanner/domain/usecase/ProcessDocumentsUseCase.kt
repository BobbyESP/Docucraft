/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.search.LocalSearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.domain.search.QuerySearchStrategy

class ProcessDocumentsUseCase(
    private val querySearchStrategy: QuerySearchStrategy,
    private val localSearchStrategy: LocalSearchStrategy,
) {
    suspend operator fun invoke(
        documents: List<ScannedDocument>,
        query: String,
        filter: FilterOptions,
        sort: SortOption,
    ): List<ScannedDocument> {
        val searched = search(documents, query)
        val filtered = filter(searched, filter)
        return sort(filtered, sort)
    }

    private suspend fun search(
        documents: List<ScannedDocument>,
        query: String,
    ): List<ScannedDocument> {
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
        documents: List<ScannedDocument>,
        filter: FilterOptions,
    ): List<ScannedDocument> {
        return documents.filterByPages(filter).filterBySize(filter).filterByDate(filter)
    }

    private fun sort(documents: List<ScannedDocument>, sort: SortOption): List<ScannedDocument> {
        return when (sort.criteria) {
            SortOption.Criteria.DATE ->
                if (sort.order == SortOption.Order.DESC)
                    documents.sortedByDescending { it.createdTimestamp }
                else documents.sortedBy { it.createdTimestamp }

            SortOption.Criteria.NAME ->
                if (sort.order == SortOption.Order.DESC)
                    documents.sortedByDescending { it.title ?: it.filename }
                else documents.sortedBy { it.title ?: it.filename }

            SortOption.Criteria.SIZE ->
                if (sort.order == SortOption.Order.DESC)
                    documents.sortedByDescending { it.fileSize }
                else documents.sortedBy { it.fileSize }
        }
    }

    private fun List<ScannedDocument>.filterByPages(filter: FilterOptions) =
        filter.minPageCount?.let { min -> filter { it.pageCount >= min } } ?: this

    private fun List<ScannedDocument>.filterBySize(filter: FilterOptions) =
        filter.minFileSize?.let { min -> filter { it.fileSize >= min } } ?: this

    private fun List<ScannedDocument>.filterByDate(filter: FilterOptions) =
        filter.dateRange?.let { (start, end) -> filter { it.createdTimestamp in start..end } }
            ?: this
}
