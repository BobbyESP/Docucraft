/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.ScannedDocumentDao
import com.bobbyesp.docucraft.feature.docscanner.data.repository.LocalDocumentsRepositoryImpl
import com.bobbyesp.docucraft.feature.docscanner.data.search.CompositeSearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.data.search.DatabaseSearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.data.search.InMemorySearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.data.service.DocumentOperationsService
import com.bobbyesp.docucraft.feature.docscanner.data.service.DocumentOperationsServiceImpl
import com.bobbyesp.docucraft.feature.docscanner.data.sharing.AndroidDocumentSharer
import com.bobbyesp.docucraft.feature.docscanner.data.sharing.FileKitDocumentExporter
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentStorageImpl
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LocalDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.search.LocalSearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.domain.search.QuerySearchStrategy
import com.bobbyesp.docucraft.feature.docscanner.domain.sharing.DocumentExporter
import com.bobbyesp.docucraft.feature.docscanner.domain.sharing.DocumentSharer
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DeleteDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.GetDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ProcessDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveScanDraftUseCase
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

    single<DocumentSharer> { AndroidDocumentSharer(context = androidContext()) }
    single<DocumentExporter> { FileKitDocumentExporter() }

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
    factory { ObserveDocumentUseCase(repository = get()) }
    factory { GetDocumentUseCase(repository = get()) }
    factory { UpdateDocumentFieldsUseCase(repository = get()) }
    factory { ProcessDocumentsUseCase(querySearchStrategy = get(), localSearchStrategy = get()) }

    factory { DeleteDocumentUseCase(repository = get(), storage = get()) }

    factory { SaveScanDraftUseCase(storage = get(), repository = get()) }
}
