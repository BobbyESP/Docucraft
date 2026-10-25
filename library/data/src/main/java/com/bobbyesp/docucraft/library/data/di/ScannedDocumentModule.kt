/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.library.data.di

import com.bobbyesp.docucraft.core.data.image.ImageLoaderComponent
import com.bobbyesp.docucraft.library.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.library.data.indexing.WorkManagerDocumentIndexQueue
import com.bobbyesp.docucraft.library.data.maintenance.WorkManagerLibraryMaintenance
import com.bobbyesp.docucraft.library.data.repository.DocumentActivityRepositoryImpl
import com.bobbyesp.docucraft.library.data.repository.DocumentsRepositoryImpl
import com.bobbyesp.docucraft.library.data.repository.FoldersRepositoryImpl
import com.bobbyesp.docucraft.library.data.repository.LinkedDocumentsRepositoryImpl
import com.bobbyesp.docucraft.library.data.repository.PagesRepositoryImpl
import com.bobbyesp.docucraft.library.data.repository.TagsRepositoryImpl
import com.bobbyesp.docucraft.library.data.search.Fts4SearchIndex
import com.bobbyesp.docucraft.library.data.service.DocumentOperationsService
import com.bobbyesp.docucraft.library.data.service.DocumentOperationsServiceImpl
import com.bobbyesp.docucraft.library.data.sharing.AndroidDocumentSharer
import com.bobbyesp.docucraft.library.data.sharing.FileKitDocumentExporter
import com.bobbyesp.docucraft.library.data.storage.AndroidExternalDocumentAccess
import com.bobbyesp.docucraft.library.data.storage.DocumentLocations
import com.bobbyesp.docucraft.library.data.storage.DocumentStorageImpl
import com.bobbyesp.docucraft.library.data.thumbnail.CachedDocumentThumbnails
import com.bobbyesp.docucraft.library.data.thumbnail.DocumentThumbnailComponent
import com.bobbyesp.docucraft.library.data.work.WorkManagerGateway
import com.bobbyesp.docucraft.library.domain.indexing.DocumentIndexQueue
import com.bobbyesp.docucraft.library.domain.maintenance.LibraryMaintenance
import com.bobbyesp.docucraft.library.domain.repository.DocumentActivityRepository
import com.bobbyesp.docucraft.library.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.library.domain.repository.FoldersRepository
import com.bobbyesp.docucraft.library.domain.repository.LinkedDocumentsRepository
import com.bobbyesp.docucraft.library.domain.repository.PagesRepository
import com.bobbyesp.docucraft.library.domain.repository.TagsRepository
import com.bobbyesp.docucraft.library.domain.search.SearchIndex
import com.bobbyesp.docucraft.library.domain.sharing.DocumentExporter
import com.bobbyesp.docucraft.library.domain.sharing.DocumentSharer
import com.bobbyesp.docucraft.library.domain.storage.DocumentStorage
import com.bobbyesp.docucraft.library.domain.storage.DocumentThumbnails
import com.bobbyesp.docucraft.library.domain.storage.ExternalDocumentAccess
import com.bobbyesp.docucraft.library.domain.suggestions.DocumentSuggester
import com.bobbyesp.docucraft.library.domain.suggestions.SuggestDocumentDetailsUseCase
import com.bobbyesp.docucraft.library.domain.usecase.ArrangeHomeSectionsUseCase
import com.bobbyesp.docucraft.library.domain.usecase.DeleteFromBinUseCase
import com.bobbyesp.docucraft.library.domain.usecase.DescribeLinkedDocumentUseCase
import com.bobbyesp.docucraft.library.domain.usecase.EmptyBinUseCase
import com.bobbyesp.docucraft.library.domain.usecase.ForgetLinkedDocumentUseCase
import com.bobbyesp.docucraft.library.domain.usecase.GetDocumentUseCase
import com.bobbyesp.docucraft.library.domain.usecase.GetReadingPositionUseCase
import com.bobbyesp.docucraft.library.domain.usecase.MoveDocumentToBinUseCase
import com.bobbyesp.docucraft.library.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.library.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.library.domain.usecase.ObserveHomeSectionsUseCase
import com.bobbyesp.docucraft.library.domain.usecase.ObserveLibraryUseCase
import com.bobbyesp.docucraft.library.domain.usecase.ObserveNotFoundDocumentsUseCase
import com.bobbyesp.docucraft.library.domain.usecase.ObserveRecentDocumentsUseCase
import com.bobbyesp.docucraft.library.domain.usecase.ProcessDocumentsUseCase
import com.bobbyesp.docucraft.library.domain.usecase.PurgeExpiredBinUseCase
import com.bobbyesp.docucraft.library.domain.usecase.ReconcileStorageUseCase
import com.bobbyesp.docucraft.library.domain.usecase.RecordDocumentAvailabilityUseCase
import com.bobbyesp.docucraft.library.domain.usecase.RecordDocumentOpenedUseCase
import com.bobbyesp.docucraft.library.domain.usecase.RegisterLinkedDocumentUseCase
import com.bobbyesp.docucraft.library.domain.usecase.RememberReadingPositionUseCase
import com.bobbyesp.docucraft.library.domain.usecase.RestoreDocumentUseCase
import com.bobbyesp.docucraft.library.domain.usecase.ResumeTextIndexingUseCase
import com.bobbyesp.docucraft.library.domain.usecase.SaveFolderUseCase
import com.bobbyesp.docucraft.library.domain.usecase.SaveLinkedToLibraryUseCase
import com.bobbyesp.docucraft.library.domain.usecase.SaveScanDraftUseCase
import com.bobbyesp.docucraft.library.domain.usecase.SaveTagUseCase
import com.bobbyesp.docucraft.library.domain.usecase.SearchDocumentsUseCase
import com.bobbyesp.docucraft.library.domain.usecase.SetDocumentFavoriteUseCase
import com.bobbyesp.docucraft.library.domain.usecase.SetDocumentTextRecognitionUseCase
import com.bobbyesp.docucraft.library.domain.usecase.SetReadingPositionMemoryUseCase
import com.bobbyesp.docucraft.library.domain.usecase.TagDocumentByNameUseCase
import com.bobbyesp.docucraft.library.domain.usecase.UpdateDocumentFieldsUseCase
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

    // Where documents wait to have their text read.
    single { WorkManagerGateway(context = androidContext()) }
    single<DocumentIndexQueue> {
        WorkManagerDocumentIndexQueue(
            workManager = get(),
            indexDocumentText = { get() },
            fallbackScope = get(qualifier = named("AppMainSupervisedScope")),
        )
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
    factory {
        SaveLinkedToLibraryUseCase(
            documents = get(),
            linked = get(),
            storage = get(),
            access = get(),
            indexQueue = get(),
            settings = get(),
        )
    }
    factory { GetReadingPositionUseCase(settings = get(), activity = get()) }
    factory { RememberReadingPositionUseCase(settings = get(), activity = get()) }
    factory { SetReadingPositionMemoryUseCase(settings = get(), activity = get()) }

    // The bin, and what keeps the library in order in the background.
    factory { MoveDocumentToBinUseCase(documents = get()) }
    factory { RestoreDocumentUseCase(documents = get(), indexQueue = get()) }
    factory { DeleteFromBinUseCase(documents = get(), storage = get(), thumbnails = get()) }
    factory { EmptyBinUseCase(documents = get(), deleteFromBin = get()) }
    factory { PurgeExpiredBinUseCase(documents = get(), deleteFromBin = get()) }
    factory {
        ReconcileStorageUseCase(
            documents = get(),
            activity = get(),
            storage = get(),
            thumbnails = get(),
        )
    }
    factory { ObserveNotFoundDocumentsUseCase(activity = get()) }
    // What proposes a title, a description, a folder and tags for a document from its text. No
    // build has one yet: binding a `DocumentSuggester` here is all it takes to turn it on.
    factory {
        SuggestDocumentDetailsUseCase(
            suggester = getOrNull<DocumentSuggester>(),
            documents = get(),
            pages = get(),
        )
    }
    single<LibraryMaintenance> {
        WorkManagerLibraryMaintenance(
            workManager = get(),
            purgeExpiredBin = { get() },
            reconcileStorage = { get() },
            fallbackScope = get(qualifier = named("AppMainSupervisedScope")),
        )
    }

    factory {
        SaveScanDraftUseCase(
            storage = get(),
            repository = get(),
            indexQueue = get(),
            settings = get(),
        )
    }
    factory { SetDocumentTextRecognitionUseCase(pages = get(), queue = get()) }

    // Organizing the library: folders, tags and favorites.
    factory { ObserveLibraryUseCase(documents = get(), tags = get()) }
    factory { ObserveHomeSectionsUseCase(folders = get(), tags = get()) }
    factory { SetDocumentFavoriteUseCase(documents = get()) }
    factory { SaveFolderUseCase(folders = get()) }
    factory { SaveTagUseCase(tags = get()) }
    factory { TagDocumentByNameUseCase(tags = get()) }
    factory { ArrangeHomeSectionsUseCase(tags = get()) }
    factory { ResumeTextIndexingUseCase(pages = get(), queue = get()) }
}
