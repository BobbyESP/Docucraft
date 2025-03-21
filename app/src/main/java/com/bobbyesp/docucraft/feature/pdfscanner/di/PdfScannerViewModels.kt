package com.bobbyesp.docucraft.feature.pdfscanner.di

import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.HomeViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val pdfScannerViewModels = module {
    viewModel { HomeViewModel(get<ScannedPdfRepository>(), get<GmsDocumentScanner>()) }
}