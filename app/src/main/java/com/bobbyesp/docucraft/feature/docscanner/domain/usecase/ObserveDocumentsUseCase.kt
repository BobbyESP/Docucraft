package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository
import kotlinx.coroutines.flow.Flow

class ObserveDocumentsUseCase(private val repository: LocalDocumentsRepository) {
    suspend operator fun invoke(): Flow<List<ScannedDocument>> {
        return repository.observeDocuments()
    }
}
