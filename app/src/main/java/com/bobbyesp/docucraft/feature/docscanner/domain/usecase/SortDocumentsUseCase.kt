package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.SortOption
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument

class SortDocumentsUseCase {

    operator fun invoke(
        documents: List<ScannedDocument>,
        sort: SortOption
    ): List<ScannedDocument> {
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
}