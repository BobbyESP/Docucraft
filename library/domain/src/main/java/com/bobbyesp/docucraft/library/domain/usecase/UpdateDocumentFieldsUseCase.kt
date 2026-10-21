/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.library.domain.usecase

import com.bobbyesp.docucraft.library.domain.repository.DocumentsRepository

/** Use case for updating PDF title and description. Single responsibility: modify PDF metadata. */
class UpdateDocumentFieldsUseCase(private val repository: DocumentsRepository) {
    suspend operator fun invoke(id: String, title: String?, description: String?) {
        require(id.isNotBlank()) { "Document ID cannot be blank" }
        repository.modifyFields(id, title, description)
    }
}
