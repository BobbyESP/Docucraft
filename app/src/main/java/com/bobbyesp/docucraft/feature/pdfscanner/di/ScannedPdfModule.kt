package com.bobbyesp.docucraft.feature.pdfscanner.di

import com.bobbyesp.docucraft.core.domain.repository.FileRepository
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.dao.ScannedPdfDao
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.repository.ScannedPdfRepositoryImpl
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.service.PdfDocumentServiceImpl
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository
import com.bobbyesp.docucraft.feature.pdfscanner.domain.service.PdfDocumentService
import com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase.*
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Dependency injection module for PDF Scanner feature.
 * Provides: repositories, services, and use cases.
 */
val pdfScannerDataModule = module {
    // Service layer
    single<PdfDocumentService> { PdfDocumentServiceImpl(context = androidContext()) }

    // Repository layer
    single<ScannedPdfRepository> {
        ScannedPdfRepositoryImpl(context = androidContext(), scannedPdfDao = get<ScannedPdfDao>())
    }

    // Use cases
    factory { GetAllScannedPdfsUseCase(repository = get()) }
    factory { SearchPdfsUseCase(repository = get()) }
    factory { GetScannedPdfByIdUseCase(repository = get()) }
    factory { GetScannedPdfByPathUseCase(repository = get()) }
    factory { UpdatePdfMetadataUseCase(repository = get()) }
    factory { CopyPdfFileUseCase(context = androidContext()) }
    factory { GeneratePdfThumbnailUseCase(pdfDocumentService = get()) }
    factory { OpenPdfInViewerUseCase(context = androidContext()) }
    factory { SharePdfUseCase(context = androidContext()) }
    factory { ScanDocumentUseCase(scannerRepository = get()) }

    factory {
        DeleteScannedPdfUseCase(
            context = androidContext(),
            repository = get(),
            fileRepository = get<FileRepository>(),
        )
    }

    factory {
        SaveScannedPdfUseCase(
            context = androidContext(),
            repository = get(),
            copyPdfFileUseCase = get(),
            generatePdfThumbnailUseCase = get(),
        )
    }
}

