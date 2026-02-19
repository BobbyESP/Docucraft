package com.bobbyesp.docucraft.feature.pdfviewer.di

import com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens.PdfViewerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val pdfViewerModule = module {
    viewModel<PdfViewerViewModel> { PdfViewerViewModel() }
}