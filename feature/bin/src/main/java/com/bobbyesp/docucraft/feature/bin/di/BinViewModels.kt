/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.bin.di

import com.bobbyesp.docucraft.feature.bin.presentation.BinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The bin's ViewModel. */
val binViewModels = module {
    viewModel { (document: BinDocumentRef) ->
        BinViewModel(
            documentUuid = document.uuid,
            documents = get(),
            restoreDocument = get(),
            deleteFromBin = get(),
            emptyBin = get(),
            stringProvider = get(),
        )
    }
}

/** A document of the bin, or none: the bin as a whole. */
data class BinDocumentRef(val uuid: String?)
