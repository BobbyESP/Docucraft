/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document

/**
 * What the library's list shows of its documents: the ones that pass the filters, in the order the
 * user chose. Searching is not part of it: a search has an order of its own, by relevance, and
 * lives in [SearchDocumentsUseCase].
 */
class ProcessDocumentsUseCase {

    operator fun invoke(
        documents: List<Document.Managed>,
        filter: FilterOptions,
        sort: SortOption,
    ): List<Document.Managed> = sort(filter(documents, filter), sort)

    private fun filter(
        documents: List<Document.Managed>,
        filter: FilterOptions,
    ): List<Document.Managed> =
        documents
            .filterByFavorite(filter)
            .filterByPages(filter)
            .filterBySize(filter)
            .filterByDate(filter)

    private fun sort(documents: List<Document.Managed>, sort: SortOption): List<Document.Managed> {
        val ascending: Comparator<Document.Managed> =
            when (sort.criteria) {
                SortOption.Criteria.DATE -> compareBy { it.createdAtEpochMillis }
                SortOption.Criteria.NAME -> compareBy { it.name }
                // A size that is not known sorts as the smallest.
                SortOption.Criteria.SIZE -> compareBy { it.sizeBytes ?: 0 }
            }
        return documents.sortedWith(
            if (sort.order == SortOption.Order.DESC) ascending.reversed() else ascending
        )
    }

    // Tags are not filtered here: which documents carry a tag is the catalogue's to answer.
    private fun List<Document.Managed>.filterByFavorite(filter: FilterOptions) =
        if (filter.favoritesOnly) filter { it.isFavorite } else this

    // A document whose pages have not been counted cannot be said to have that many.
    private fun List<Document.Managed>.filterByPages(filter: FilterOptions) =
        filter.minPageCount?.let { min -> filter { (it.pageCount ?: 0) >= min } } ?: this

    private fun List<Document.Managed>.filterBySize(filter: FilterOptions) =
        filter.minFileSize?.let { min -> filter { (it.sizeBytes ?: 0) >= min } } ?: this

    private fun List<Document.Managed>.filterByDate(filter: FilterOptions) =
        filter.dateRange?.let { range -> filter { it.createdAtEpochMillis in range } } ?: this
}
