package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository

/** Use case for updating PDF title and description. Single responsibility: modify PDF metadata. */
class UpdateDocumentFieldsUseCase(private val repository: LocalDocumentsRepository) {
    suspend operator fun invoke(id: String, title: String?, description: String?) {
        require(id.isNotBlank()) { "Document ID cannot be blank" }
        repository.modifyFields(id, title, description)
    }
}
