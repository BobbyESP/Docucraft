/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.search.di

import com.bobbyesp.docucraft.feature.search.presentation.DocumentSearchViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The ViewModel of the library's search.
 *
 * Written as `viewModel { }` lambdas rather than `viewModelOf(::…)`, so that a ViewModel's
 * dispatcher falls back to its Kotlin default instead of needing a binding in the graph.
 */
val searchViewModels = module {
    viewModel {
        DocumentSearchViewModel(
            savedStateHandle = get(),
            observeDocumentsUseCase = get(),
            searchDocumentsUseCase = get(),
            observeNotFoundDocuments = get(),
            stringProvider = get(),
            analyticsHelper = get(),
        )
    }
}
