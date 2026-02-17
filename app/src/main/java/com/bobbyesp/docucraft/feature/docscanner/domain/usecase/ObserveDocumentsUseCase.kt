package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.ScannedDocumentsRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for retrieving all scanned PDFs as a flow. Follows Single Responsibility Principle -
 * handles only one business action.
 */
class ObserveDocumentsUseCase(private val repository: ScannedDocumentsRepository) {
    suspend operator fun invoke(): Flow<List<ScannedDocument>> {
        return repository.observeDocuments()
    }
}
