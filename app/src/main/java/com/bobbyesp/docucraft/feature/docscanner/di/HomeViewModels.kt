/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Home's ViewModel.
 *
 * Written as `viewModel { }` lambdas rather than `viewModelOf(::…)`, so that a ViewModel's
 * dispatcher falls back to its Kotlin default instead of needing a binding in the graph.
 */
val homeViewModels = module {
    viewModel {
        HomeViewModel(
            savedStateHandle = get(),
            documentScanner = get(),
            scanRequests = get(),
            observeDocumentsUseCase = get(),
            observeRecentDocumentsUseCase = get(),
            observeLibraryUseCase = get(),
            observeHomeSectionsUseCase = get(),
            tags = get(),
            observeNotFoundDocuments = get(),
            processDocumentsUseCase = get(),
            saveScanDraftUseCase = get(),
            settings = get(),
            stringProvider = get(),
            analyticsHelper = get(),
        )
    }
}
