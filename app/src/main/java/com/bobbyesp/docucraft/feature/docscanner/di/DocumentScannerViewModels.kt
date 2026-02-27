package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.feature.docscanner.domain.ScannerManager
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DeleteDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ExportDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.GetDocumentByIdUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.OpenDocumentInViewerUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveScannedDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SearchDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ShareDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.UpdateDocumentFieldsUseCase
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.sheet.DocumentSheetViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val documentScannerViewModels = module {
    viewModel {
        HomeViewModel(
            scannerManager = get<ScannerManager>(),
            observeDocumentsUseCase = get<ObserveDocumentsUseCase>(),
            searchDocumentsUseCase = get<SearchDocumentsUseCase>(),
            getDocumentByIdUseCase = get<GetDocumentByIdUseCase>(),
            saveScannedDocumentUseCase = get<SaveScannedDocumentUseCase>(),
            deleteDocumentUseCase = get<DeleteDocumentUseCase>(),
            openDocumentInViewerUseCase = get<OpenDocumentInViewerUseCase>(),
            shareDocumentUseCase = get<ShareDocumentUseCase>(),
            exportDocumentUseCase = get<ExportDocumentUseCase>(),
            stringProvider = get<StringProvider>(),
            analyticsHelper = get<AnalyticsHelper>(),
        )
    }

    viewModel {
        DocumentSheetViewModel(
            observeDocumentsUseCase = get<ObserveDocumentsUseCase>(),
            getDocumentByIdUseCase = get<GetDocumentByIdUseCase>(),
            updateDocumentFieldsUseCase = get<UpdateDocumentFieldsUseCase>(),
            stringProvider = get<StringProvider>(),
        )
    }
}
