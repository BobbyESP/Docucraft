/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.indexing.DocumentIndexQueue
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LinkedDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.ExternalDocumentAccess
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/** What came of saving another app's document into the library. Each one is an answer. */
sealed interface SaveToLibraryOutcome {

    /** It is now a document the app keeps, with the same uuid. */
    data object Saved : SaveToLibraryOutcome

    /**
     * Nothing was saved, because the library already has a document with the same content:
     * [existingUuid]. The user may still want a second copy, and can ask for it.
     */
    data class AlreadyInLibrary(val existingUuid: String) : SaveToLibraryOutcome

    /**
     * It could not be copied, or what was copied cannot be opened: the file is out of reach,
     * protected with a password or damaged. The library only keeps what it can show.
     */
    data object NotReadable : SaveToLibraryOutcome

    /** There is no linked document to save: it is gone, or was saved already. */
    data object NothingToSave : SaveToLibraryOutcome
}

/**
 * Saves a document of another app into the library: copies its file and turns the reference into a
 * document the app keeps. It is the same document afterwards, with the uuid it had, when it was
 * opened and where it was left; it only stops depending on the other app.
 *
 * The file first, then the catalogue, as when a scan is saved: a row never points at a file that is
 * not whole. If the catalogue then fails, the copy is removed.
 */
class SaveLinkedToLibraryUseCase(
    private val documents: DocumentsRepository,
    private val linked: LinkedDocumentsRepository,
    private val storage: DocumentStorage,
    private val access: ExternalDocumentAccess,
    private val indexQueue: DocumentIndexQueue,
    private val settings: SettingsRepository,
) {
    /**
     * @param evenIfAlreadyThere Saves it although the library has a document with the same content.
     *   Asked for only after [SaveToLibraryOutcome.AlreadyInLibrary] was answered.
     */
    suspend operator fun invoke(
        documentUuid: String,
        evenIfAlreadyThere: Boolean = false,
    ): SaveToLibraryOutcome {
        val document =
            runCatching { documents.getDocument(documentUuid) }.getOrNull() as? Document.Linked
                ?: return SaveToLibraryOutcome.NothingToSave

        val stored =
            try {
                storage.storeDocument(document.location, documentUuid)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return SaveToLibraryOutcome.NotReadable
            }

        // A file the platform cannot open has no pages to count.
        if (stored.pageCount == null) {
            storage.delete(stored.filePath)
            return SaveToLibraryOutcome.NotReadable
        }

        if (!evenIfAlreadyThere) {
            val existing = documents.findInLibrary(stored.contentHash)
            if (existing != null) {
                storage.delete(stored.filePath)
                return SaveToLibraryOutcome.AlreadyInLibrary(existing.uuid)
            }
        }

        val kept =
            try {
                linked.keepInLibrary(
                    documentUuid,
                    stored,
                    // What the user last chose for the documents they save.
                    recognizeText = settings.settings.first().recognizeTextInNewDocuments,
                )
            } catch (failure: Exception) {
                storage.delete(stored.filePath)
                throw failure
            }
        if (!kept) {
            // It stopped being a linked document while its file was being copied.
            storage.delete(stored.filePath)
            return SaveToLibraryOutcome.NothingToSave
        }

        // The app reads its own copy from here on.
        access.release(document.location)
        // It can be searched now, so its text is read. If it cannot be queued here, it is when
        // the app starts.
        runCatching { indexQueue.enqueue(documentUuid) }

        return SaveToLibraryOutcome.Saved
    }
}
