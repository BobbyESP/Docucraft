/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.scan.di

import com.bobbyesp.docucraft.feature.scan.presentation.ScanReviewViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The ViewModel of the review of a scan just saved. */
val scanReviewViewModels = module {
    viewModel { (documentUuid: String) ->
        ScanReviewViewModel(
            documentUuid = documentUuid,
            observeDocument = get(),
            folders = get(),
            tags = get(),
            updateDocumentFields = get(),
            setTextRecognition = get(),
            tagByName = get(),
            suggestDetails = get(),
        )
    }
}
