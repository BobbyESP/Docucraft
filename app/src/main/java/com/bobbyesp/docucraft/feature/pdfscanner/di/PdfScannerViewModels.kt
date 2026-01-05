package com.bobbyesp.docucraft.feature.pdfscanner.di

import com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase.*
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.viewmodel.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val pdfScannerViewModels = module {
    viewModel {
        HomeViewModel(
            getAllScannedPdfsUseCase = get<GetAllScannedPdfsUseCase>(),
            searchPdfsUseCase = get<SearchPdfsUseCase>(),
            getScannedPdfByIdUseCase = get<GetScannedPdfByIdUseCase>(),
            saveScannedPdfUseCase = get<SaveScannedPdfUseCase>(),
            deleteScannedPdfUseCase = get<DeleteScannedPdfUseCase>(),
            updatePdfMetadataUseCase = get<UpdatePdfMetadataUseCase>(),
            openPdfInViewerUseCase = get<OpenPdfInViewerUseCase>(),
            sharePdfUseCase = get<SharePdfUseCase>(),
            scanDocumentUseCase = get<ScanDocumentUseCase>(),
        )
    }
}
