package com.bobbyesp.docucraft.feature.pdfscanner.di

import com.bobbyesp.docucraft.core.domain.repository.FileRepository
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.dao.ScannedPdfDao
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.repository.PdfDocumentHelperImpl
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.repository.ScannedPdfRepositoryImpl
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.PdfDocumentHelper
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val scannedPdfModule = module {
    single<PdfDocumentHelper> {
        PdfDocumentHelperImpl(context = androidContext())
    }

    single<ScannedPdfRepository> {
        ScannedPdfRepositoryImpl(
            context = androidContext(),
            fileRepository = get<FileRepository>(),
            scannedPdfDao = get<ScannedPdfDao>(),
            pdfDocsHelper = get<PdfDocumentHelper>()
        )
    }
}
