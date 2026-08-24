/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.di

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
            scannerManager = get(),
            observeDocumentsUseCase = get(),
            processDocumentsUseCase = get(),
            getDocumentUseCase = get(),
            saveScannedDocumentUseCase = get(),
            deleteDocumentUseCase = get(),
            shareDocumentUseCase = get(),
            exportDocumentUseCase = get(),
            updateDocumentFieldsUseCase = get(),
            stringProvider = get(),
            analyticsHelper = get(),
        )
    }
}
