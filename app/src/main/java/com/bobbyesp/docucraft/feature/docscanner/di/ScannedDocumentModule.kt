/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.core.data.image.ImageLoaderComponent
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.repository.DocumentActivityRepositoryImpl
import com.bobbyesp.docucraft.feature.docscanner.data.repository.DocumentsRepositoryImpl
import com.bobbyesp.docucraft.feature.docscanner.data.repository.FoldersRepositoryImpl
import com.bobbyesp.docucraft.feature.docscanner.data.repository.LinkedDocumentsRepositoryImpl
import com.bobbyesp.docucraft.feature.docscanner.data.repository.PagesRepositoryImpl
import com.bobbyesp.docucraft.feature.docscanner.data.repository.TagsRepositoryImpl
import com.bobbyesp.docucraft.feature.docscanner.data.search.Fts4SearchIndex
import com.bobbyesp.docucraft.feature.docscanner.data.service.DocumentOperationsService
import com.bobbyesp.docucraft.feature.docscanner.data.service.DocumentOperationsServiceImpl
import com.bobbyesp.docucraft.feature.docscanner.data.sharing.AndroidDocumentSharer
import com.bobbyesp.docucraft.feature.docscanner.data.sharing.FileKitDocumentExporter
import com.bobbyesp.docucraft.feature.docscanner.data.storage.AndroidExternalDocumentAccess
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentLocations
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentStorageImpl
import com.bobbyesp.docucraft.feature.docscanner.data.thumbnail.CachedDocumentThumbnails
import com.bobbyesp.docucraft.feature.docscanner.data.thumbnail.DocumentThumbnailComponent
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentActivityRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FoldersRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LinkedDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.PagesRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.TagsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.search.SearchIndex
import com.bobbyesp.docucraft.feature.docscanner.domain.sharing.DocumentExporter
import com.bobbyesp.docucraft.feature.docscanner.domain.sharing.DocumentSharer
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentThumbnails
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.ExternalDocumentAccess
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DeleteDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DescribeLinkedDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ForgetLinkedDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.GetDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.GetReadingPositionUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveRecentDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ProcessDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.RecordDocumentAvailabilityUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.RecordDocumentOpenedUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.RegisterLinkedDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.RememberReadingPositionUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveScanDraftUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SearchDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SetReadingPositionMemoryUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.UpdateDocumentFieldsUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
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

    single<DocumentThumbnails> {
        CachedDocumentThumbnails(
            context = androidContext(),
            documentDao = get(),
            documentOperations = get(),
        )
    }

    // How the image loader, which core builds, learns to show a document's preview.
    single<ImageLoaderComponent>(named("documentThumbnails")) {
        DocumentThumbnailComponent(thumbnails = get())
    }

    single<DocumentSharer> { AndroidDocumentSharer(context = androidContext()) }
    single<DocumentExporter> { FileKitDocumentExporter() }

    // Repository layer
    single { DocumentLocations(context = androidContext()) }

    single<DocumentsRepository> {
        DocumentsRepositoryImpl(documentDao = get(), locations = get())
    }
    single<DocumentActivityRepository> {
        DocumentActivityRepositoryImpl(activityDao = get(), locations = get())
    }
    single<LinkedDocumentsRepository> { LinkedDocumentsRepositoryImpl(documentDao = get()) }
    single<ExternalDocumentAccess> { AndroidExternalDocumentAccess(context = androidContext()) }
    single<FoldersRepository> { FoldersRepositoryImpl(database = get(), locations = get()) }
    single<TagsRepository> { TagsRepositoryImpl(database = get(), locations = get()) }
    single<PagesRepository> {
        PagesRepositoryImpl(pageDao = get<DocumentsDatabase>().pageDao())
    }

    // How the library is searched: the one line that changes for another search engine.
    single<SearchIndex> { Fts4SearchIndex(searchDao = get()) }

    // Use cases
    factory { ObserveDocumentsUseCase(repository = get()) }
    factory { ObserveDocumentUseCase(repository = get()) }
    factory { GetDocumentUseCase(repository = get()) }
    factory { UpdateDocumentFieldsUseCase(repository = get()) }
    factory { ProcessDocumentsUseCase() }
    factory { SearchDocumentsUseCase(searchIndex = get()) }
    factory { ObserveRecentDocumentsUseCase(activity = get()) }
    factory { RecordDocumentOpenedUseCase(activity = get()) }
    factory { RecordDocumentAvailabilityUseCase(activity = get()) }
    factory { RegisterLinkedDocumentUseCase(access = get(), linked = get()) }
    factory { ForgetLinkedDocumentUseCase(linked = get(), access = get()) }
    factory { DescribeLinkedDocumentUseCase(documents = get(), linked = get(), access = get()) }
    factory { GetReadingPositionUseCase(settings = get(), activity = get()) }
    factory { RememberReadingPositionUseCase(settings = get(), activity = get()) }
    factory { SetReadingPositionMemoryUseCase(settings = get(), activity = get()) }

    factory { DeleteDocumentUseCase(repository = get(), storage = get(), thumbnails = get()) }

    factory { SaveScanDraftUseCase(storage = get(), repository = get()) }
}
