package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.repository.ScannedDocumentsRepository

/** Use case for updating PDF title and description. Single responsibility: modify PDF metadata. */
class UpdateDocumentFieldsUseCase(private val repository: ScannedDocumentsRepository) {
    suspend operator fun invoke(id: String, title: String?, description: String?) {
        require(id.isNotBlank()) { "PDF ID cannot be blank" }
        repository.modifyFields(id, title, description)
    }
}
