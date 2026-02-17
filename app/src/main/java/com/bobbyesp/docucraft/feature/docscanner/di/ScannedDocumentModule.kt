package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.core.domain.repository.FileRepository
import com.bobbyesp.docucraft.feature.docscanner.data.local.db.dao.ScannedDocumentDao
import com.bobbyesp.docucraft.feature.docscanner.data.local.repository.ScannedDocumentsRepositoryImpl
import com.bobbyesp.docucraft.feature.docscanner.data.local.service.DocumentOperationsServiceImpl
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.ScannedDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.service.DocumentOperationsService
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.*
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Dependency injection module for Document Scanner feature. Provides: repositories, services, and use
 * cases.
 */
val documentScannerDataModule = module {
    // Service layer
    single<DocumentOperationsService> { DocumentOperationsServiceImpl(context = androidContext()) }

    // Repository layer
    single<ScannedDocumentsRepository> {
        ScannedDocumentsRepositoryImpl(context = androidContext(), scannedDocumentDao = get<ScannedDocumentDao>())
    }

    // Use cases
    factory { ObserveDocumentsUseCase(repository = get()) }
    factory { SearchDocumentsUseCase(repository = get()) }
    factory { GetDocumentByIdUseCase(repository = get()) }
    factory { GetDocumentByPathUseCase(repository = get()) }
    factory { UpdateDocumentFieldsUseCase(repository = get()) }
    factory { CopyDocumentToFileUseCase(context = androidContext()) }
    factory { GenerateDocumentThumbnailUseCase(documentOperationsService = get()) }
    factory { OpenDocumentInViewerUseCase(context = androidContext()) }
    factory { ShareDocumentUseCase(context = androidContext()) }
    factory { ScanDocumentUseCase(scannerRepository = get()) }

    factory {
        DeleteDocumentUseCase(
            context = androidContext(),
            repository = get(),
            fileRepository = get<FileRepository>(),
        )
    }

    factory {
        SaveScannedDocumentUseCase(
            context = androidContext(),
            repository = get(),
            copyDocumentToFileUseCase = get(),
            generateDocumentThumbnailUseCase = get(),
        )
    }
}
