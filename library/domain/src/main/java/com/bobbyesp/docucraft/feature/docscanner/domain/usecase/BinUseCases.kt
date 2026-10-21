/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.indexing.DocumentIndexQueue
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentAvailability
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentActivityRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentStorage
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentThumbnails
import kotlinx.coroutines.flow.first

/*
 * Deleting is reversible. A document the user deletes goes to the bin, where it keeps everything
 * and can be brought back; it is only deleted for good from there, by the user or once it has been
 * there for as long as the bin keeps things. Nothing else ever deletes a document the app keeps.
 */

/** How long the bin keeps a document. */
object BinRetention {
    const val DAYS = 30

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** For how long a document stays in the bin, in milliseconds. */
    const val MILLIS = DAYS * DAY_MILLIS

    /**
     * The days a document sent to the bin at [binnedAtEpochMillis] still has there, counting the
     * one that has started: never less than none.
     */
    fun daysLeft(binnedAtEpochMillis: Long, nowEpochMillis: Long): Int {
        val left = binnedAtEpochMillis + MILLIS - nowEpochMillis
        return if (left <= 0) 0 else ((left + DAY_MILLIS - 1) / DAY_MILLIS).toInt()
    }
}

/** What the user means by deleting a document: it goes to the bin. */
class MoveDocumentToBinUseCase(private val documents: DocumentsRepository) {
    /** @return Whether it went: `false` for a document that was already there, or is gone. */
    suspend operator fun invoke(documentUuid: String): Boolean = documents.moveToBin(documentUuid)
}

/**
 * Brings a document back from the bin. Its text is not read while it is there, so what was still to
 * be read when it went is queued again.
 */
class RestoreDocumentUseCase(
    private val documents: DocumentsRepository,
    private val indexQueue: DocumentIndexQueue,
) {
    suspend operator fun invoke(documentUuid: String): Boolean {
        val restored = documents.restoreFromBin(documentUuid)
        if (restored) indexQueue.enqueue(documentUuid)
        return restored
    }
}

/**
 * Deletes a document of the bin for good: the catalogue first, then its file, then its previews.
 *
 * In that order because a row pointing at a file that is gone is worse than a file nothing points
 * at, and only the first is visible to the user. A file that could not be removed is left for
 * [ReconcileStorageUseCase], which finds it without a row.
 */
class DeleteFromBinUseCase(
    private val documents: DocumentsRepository,
    private val storage: DocumentStorage,
    private val thumbnails: DocumentThumbnails,
) {
    /** @return Whether it was deleted: `false` for a document that is not in the bin. */
    suspend operator fun invoke(document: Document.Managed): Boolean {
        if (!documents.deleteFromBin(document.uuid)) return false

        runCatching {
            // Two documents brought over from an older catalogue can share a file. It is only
            // removed with the last of them.
            if (document.filePath !in documents.keptFiles().values) {
                storage.delete(document.filePath)
            }
        }
        runCatching { thumbnails.discard(document.uuid) }
        return true
    }
}

/** Deletes for good everything that is in the bin. */
class EmptyBinUseCase(
    private val documents: DocumentsRepository,
    private val deleteFromBin: DeleteFromBinUseCase,
) {
    /** @return How many documents were deleted. */
    suspend operator fun invoke(): Int = documents.observeBin().first().count { deleteFromBin(it) }
}

/**
 * Deletes for good what has been in the bin for as long as the bin keeps things.
 *
 * @param now The clock, in epoch milliseconds. A parameter so that a test can hold it still.
 */
class PurgeExpiredBinUseCase(
    private val documents: DocumentsRepository,
    private val deleteFromBin: DeleteFromBinUseCase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** @return How many documents were deleted. */
    suspend operator fun invoke(): Int =
        documents.binnedUntil(now() - BinRetention.MILLIS).count { deleteFromBin(it) }
}

/**
 * What a reconciliation found and did.
 *
 * @property deletedFiles The files nothing referred to, which were removed.
 * @property notFound The documents whose file is not there, newly marked as not found.
 * @property foundAgain The documents marked as not found whose file is there again.
 */
data class Reconciliation(
    val deletedFiles: List<String> = emptyList(),
    val notFound: List<String> = emptyList(),
    val foundAgain: List<String> = emptyList(),
)

/**
 * Checks that the catalogue and the files agree, and says so where they do not.
 *
 * - A file no document refers to is removed, once it is older than [ORPHAN_MARGIN_MILLIS]. A
 *   document is stored before it is catalogued, so a file without a row may be one that is being
 *   saved right now; an old one is what a save that died half way, or a delete that could not
 *   remove its file, left behind.
 * - A document whose file is not there is marked as not found, and shown so. It is never deleted:
 *   the file may come back, with a restored backup, and deleting it is the user's to decide.
 * - A document marked as not found whose file is there again stops being so.
 *
 * @param now The clock, in epoch milliseconds.
 */
class ReconcileStorageUseCase(
    private val documents: DocumentsRepository,
    private val activity: DocumentActivityRepository,
    private val storage: DocumentStorage,
    private val thumbnails: DocumentThumbnails,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend operator fun invoke(): Reconciliation {
        // Read before the files are listed: a document saved in between has its file listed and
        // no row here, and is too new to be touched.
        val kept = documents.keptFiles()
        val referred = kept.values.toSet()
        val oldEnough = now() - ORPHAN_MARGIN_MILLIS

        val deleted =
            storage
                .storedFiles()
                .filter { it.filePath !in referred && it.lastModifiedEpochMillis <= oldEnough }
                .map { it.filePath }
                .onEach { storage.delete(it) }

        val marked = activity.observeNotFound().first()
        val notFound = mutableListOf<String>()
        val foundAgain = mutableListOf<String>()
        for ((uuid, filePath) in kept) {
            val exists = storage.exists(filePath)
            if (!exists && uuid !in marked) {
                activity.recordAvailability(uuid, DocumentAvailability.NOT_FOUND)
                notFound += uuid
            } else if (exists && uuid in marked) {
                activity.recordAvailability(uuid, DocumentAvailability.AVAILABLE)
                foundAgain += uuid
            }
        }

        // Previews are a cache: those of documents that are gone are of no use to anybody.
        runCatching { thumbnails.retainOnly(kept.keys) }

        return Reconciliation(deleted, notFound, foundAgain)
    }

    companion object {
        /** How old a file nothing refers to has to be before it is removed: a day. */
        const val ORPHAN_MARGIN_MILLIS = 24L * 60 * 60 * 1000
    }
}
