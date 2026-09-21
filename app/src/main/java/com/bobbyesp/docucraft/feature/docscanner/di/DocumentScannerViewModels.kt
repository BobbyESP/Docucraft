/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.actions.DocumentActionsViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel.HomeViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

// A manual `viewModel { }` lambda instead of `viewModelOf(::HomeViewModel)` so HomeViewModel's
// `defaultDispatcher` constructor parameter falls back to its Kotlin default (Dispatchers.Default)
// rather than requiring a CoroutineDispatcher binding in the Koin graph.
val documentScannerViewModels = module {
    viewModel {
        HomeViewModel(
            savedStateHandle = get(),
            documentScanner = get(),
            scanRequests = get(),
            observeDocumentsUseCase = get(),
            processDocumentsUseCase = get(),
            saveScanDraftUseCase = get(),
            stringProvider = get(),
            analyticsHelper = get(),
        )
    }

    // Scoped to the navigation entry acting on the document, so the uuid comes from the key rather
    // than from a graph binding.
    viewModel { (documentUuid: String) ->
        DocumentActionsViewModel(
            documentUuid = documentUuid,
            observeDocument = get(),
            deleteDocumentUseCase = get(),
            updateDocumentFieldsUseCase = get(),
            documentSharer = get(),
            documentExporter = get(),
            stringProvider = get(),
            analyticsHelper = get(),
        )
    }
}
