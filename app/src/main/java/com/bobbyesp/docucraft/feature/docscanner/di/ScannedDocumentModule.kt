/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.core.domain.repository.FileRepository
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.ScannedDocumentDao
import com.bobbyesp.docucraft.feature.docscanner.data.repository.LocalDocumentsRepositoryImpl
import com.bobbyesp.docucraft.feature.docscanner.data.search.CompositeSearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.data.search.DatabaseSearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.data.search.InMemorySearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.data.service.DocumentOperationsServiceImpl
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentStorageImpl
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.search.LocalSearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.domain.search.QuerySearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.domain.service.DocumentOperationsService
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DeleteDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ExportDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.GetDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.OpenDocumentInViewerUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ProcessDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveScanDraftUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ShareDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.UpdateDocumentFieldsUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Dependency injection module for Document Scanner feature. Provides: repositories, services, and
 * use cases.
 */
val documentScannerDataModule = module {
    // Service layer
    single<DocumentOperationsService> { DocumentOperationsServiceImpl(context = androidContext()) }

    single<DocumentStorage> {
        DocumentStorageImpl(context = androidContext(), documentOperations = get())
    }

    // Repository layer
    single<LocalDocumentsRepository> {
        LocalDocumentsRepositoryImpl(scannedDocumentDao = get<ScannedDocumentDao>())
    }

    // Search Strategies
    factory<LocalSearchStrategy> { InMemorySearchStrategy() }
    factory<QuerySearchStrategy> {
        CompositeSearchStrategy(listOf(DatabaseSearchStrategy(repository = get())))
    }

    // Use cases
    factory { ObserveDocumentsUseCase(repository = get()) }
    factory { GetDocumentUseCase(repository = get()) }
    factory { UpdateDocumentFieldsUseCase(repository = get()) }
    factory { OpenDocumentInViewerUseCase(context = androidContext()) }
    factory { ShareDocumentUseCase(context = androidContext()) }
    factory { ExportDocumentUseCase() }
    factory { ProcessDocumentsUseCase(querySearchStrategy = get(), localSearchStrategy = get()) }

    factory { DeleteDocumentUseCase(repository = get(), fileRepository = get<FileRepository>()) }

    factory { SaveScanDraftUseCase(storage = get(), repository = get()) }
}
