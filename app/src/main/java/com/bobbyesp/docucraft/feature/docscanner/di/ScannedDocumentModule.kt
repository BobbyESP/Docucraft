/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.core.data.image.ImageLoaderComponent
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.indexing.WorkManagerDocumentIndexQueue
import com.bobbyesp.docucraft.feature.docscanner.data.maintenance.WorkManagerLibraryMaintenance
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
import com.bobbyesp.docucraft.feature.docscanner.domain.indexing.DocumentIndexQueue
import com.bobbyesp.docucraft.feature.docscanner.domain.maintenance.LibraryMaintenance
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
import com.bobbyesp.docucraft.feature.docscanner.domain.suggestions.DocumentSuggester
import com.bobbyesp.docucraft.feature.docscanner.domain.suggestions.SuggestDocumentDetailsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ArrangeHomeSectionsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DeleteFromBinUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DescribeLinkedDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.EmptyBinUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ForgetLinkedDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.GetDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.GetReadingPositionUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.IndexDocumentTextUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.MoveDocumentToBinUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveHomeSectionsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveLibraryUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveNotFoundDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveRecentDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ProcessDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.PurgeExpiredBinUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ReconcileStorageUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.RecordDocumentAvailabilityUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.RecordDocumentOpenedUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.RegisterLinkedDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.RememberReadingPositionUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.RestoreDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ResumeTextIndexingUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveFolderUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveLinkedToLibraryUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveScanDraftUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveTagUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SearchDocumentsUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SetDocumentFavoriteUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SetDocumentTextRecognitionUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SetReadingPositionMemoryUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.TagDocumentByNameUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.UpdateDocumentFieldsUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.di.EMBEDDED_TEXT
import com.bobbyesp.docucraft.feature.pdfviewer.di.TEXT_RECOGNITION
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
    single<DocumentIndexQueue> { WorkManagerDocumentIndexQueue(context = androidContext()) }
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
    single<LibraryMaintenance> { WorkManagerLibraryMaintenance(context = androidContext()) }

    factory {
        SaveScanDraftUseCase(
            storage = get(),
            repository = get(),
            indexQueue = get(),
            settings = get(),
        )
    }
    factory {
        IndexDocumentTextUseCase(
            documents = get(),
            pages = get(),
            storage = get(),
            embedded = get(named(EMBEDDED_TEXT)),
            recognized = get(named(TEXT_RECOGNITION)),
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
