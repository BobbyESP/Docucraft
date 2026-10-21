/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.documentactions.di

import com.bobbyesp.docucraft.feature.documentactions.presentation.DocumentActionsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The ViewModel of a document's actions: its sheet, editing and deleting it. */
val documentActionsViewModels = module {
    // Scoped to the navigation entry acting on the document, so the uuid comes from the key rather
    // than from a graph binding.
    viewModel { (documentUuid: String) ->
        DocumentActionsViewModel(
            documentUuid = documentUuid,
            observeDocument = get(),
            moveDocumentToBin = get(),
            updateDocumentFieldsUseCase = get(),
            documentSharer = get(),
            documentExporter = get(),
            forgetLinkedDocument = get(),
            setTextRecognition = get(),
            setFavorite = get(),
            stringProvider = get(),
            analyticsHelper = get(),
        )
    }
}
