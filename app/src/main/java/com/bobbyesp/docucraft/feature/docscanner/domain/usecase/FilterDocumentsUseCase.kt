package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.FilterOptions
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument

class FilterDocumentsUseCase {

    operator fun invoke(
        documents: List<ScannedDocument>,
        filter: FilterOptions
    ): List<ScannedDocument> {
        return documents
            .filterByPages(filter)
            .filterBySize(filter)
            .filterByDate(filter)
    }

    private fun List<ScannedDocument>.filterByPages(filter: FilterOptions) =
        filter.minPageCount?.let { min ->
            filter { it.pageCount >= min }
        } ?: this

    private fun List<ScannedDocument>.filterBySize(filter: FilterOptions) =
        filter.minFileSize?.let { min ->
            filter { it.fileSize >= min }
        } ?: this

    private fun List<ScannedDocument>.filterByDate(filter: FilterOptions) =
        filter.dateRange?.let { (start, end) ->
            filter { it.createdTimestamp in start..end }
        } ?: this
}