package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.core.domain.repository.FileRepository
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.ScannedDocumentDao
import com.bobbyesp.docucraft.feature.docscanner.data.repository.LocalDocumentsRepositoryImpl
import com.bobbyesp.docucraft.feature.docscanner.data.service.DocumentOperationsServiceImpl
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.search.CompositeSearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.domain.search.DatabaseSearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.domain.search.InMemorySearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.domain.search.LocalSearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.domain.search.QuerySearchStrategy
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
    single<LocalDocumentsRepository> {
        LocalDocumentsRepositoryImpl(scannedDocumentDao = get<ScannedDocumentDao>())
    }

    // Search Strategies
    factory<LocalSearchStrategy> { InMemorySearchStrategy() }
    factory<QuerySearchStrategy> {
        CompositeSearchStrategy(
            listOf(DatabaseSearchStrategy(repository = get()))
        )
    }

    // Use cases
    factory { ObserveDocumentsUseCase(repository = get()) }
    factory { GetDocumentByIdUseCase(repository = get()) }
    factory { GetDocumentByPathUseCase(repository = get()) }
    factory { UpdateDocumentFieldsUseCase(repository = get()) }
    factory { CopyDocumentToFileUseCase(context = androidContext()) }
    factory { GenerateDocumentThumbnailUseCase(documentOperationsService = get()) }
    factory { OpenDocumentInViewerUseCase(context = androidContext()) }
    factory { ShareDocumentUseCase(context = androidContext()) }
    factory { ExportDocumentUseCase() }
    factory { SortDocumentsUseCase() }
    factory { FilterDocumentsUseCase() }
    factory { SearchDocumentsUseCase(queryStrategy = get(), localStrategy = get()) }

    factory {
        DeleteDocumentUseCase(
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
