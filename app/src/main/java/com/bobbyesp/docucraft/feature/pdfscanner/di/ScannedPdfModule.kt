package com.bobbyesp.docucraft.feature.pdfscanner.di

import com.bobbyesp.docucraft.core.domain.repository.FileRepository
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.dao.ScannedPdfDao
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.repository.PdfDocumentHelperImpl
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.repository.ScannedPdfRepositoryImpl
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.repository.usecase.scannedpdf.PdfFileManagementUseCaseImpl
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.repository.usecase.scannedpdf.ScannedPdfUseCaseImpl
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.PdfDocumentHelper
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.usecase.PdfFileManagementUseCase
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.usecase.ScannedPdfUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val scannedPdfModule = module {
    single<PdfDocumentHelper> { PdfDocumentHelperImpl(context = androidContext()) }

    single<PdfFileManagementUseCase> { PdfFileManagementUseCaseImpl(androidContext()) }

    single<ScannedPdfRepository> {
        ScannedPdfRepositoryImpl(context = androidContext(), scannedPdfDao = get<ScannedPdfDao>())
    }

    single<ScannedPdfUseCase> {
        ScannedPdfUseCaseImpl(
            context = androidContext(),
            repository = get<ScannedPdfRepository>(),
            fileRepository = get<FileRepository>(),
            pdfFileManagementUseCase = get<PdfFileManagementUseCase>(),
            pdfDocsHelper = get<PdfDocumentHelper>(),
        )
    }
}
