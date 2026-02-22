package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.core.domain.usecase.NotifyUserUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.*
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val documentScannerViewModels = module {
    viewModel {
        HomeViewModel(
            observeDocumentsUseCase = get<ObserveDocumentsUseCase>(),
            searchDocumentsUseCase = get<SearchDocumentsUseCase>(),
            getDocumentByIdUseCase = get<GetDocumentByIdUseCase>(),
            saveScannedDocumentUseCase = get<SaveScannedDocumentUseCase>(),
            deleteDocumentUseCase = get<DeleteDocumentUseCase>(),
            updateDocumentFieldsUseCase = get<UpdateDocumentFieldsUseCase>(),
            openDocumentInViewerUseCase = get<OpenDocumentInViewerUseCase>(),
            shareDocumentUseCase = get<ShareDocumentUseCase>(),
            scanDocumentUseCase = get<ScanDocumentUseCase>(),
            notifyUserUseCase = get<NotifyUserUseCase>(),
        )
    }
}
