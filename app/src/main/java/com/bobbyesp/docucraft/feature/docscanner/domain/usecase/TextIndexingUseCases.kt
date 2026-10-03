/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.indexing.DocumentIndexQueue
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.PageTextStatus
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.PageTextRecord
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.PagesRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentStorage
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.documentcontent.PageContentProvider
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.PageContentSession
import com.bobbyesp.documentcontent.PageText
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reads the text of the pages of a document the app keeps that are still to be read, and writes it
 * down, so that the document can be found by what it says. The viewer reads the same text for the
 * pages on screen and forgets it; this is that, for the whole document, kept.
 *
 * Page by page, each written as it is read: if the app is closed half way, what was read stays and
 * the rest is still pending.
 *
 * The text comes from the same [PageContentProvider] the viewer uses. A page it finds no text on,
 * or cannot look at on this device, is noted as waiting for text recognition, which the user has
 * not turned on for the document.
 *
 * Nothing here is an error for the caller: a document that is gone, in the bin or another app's is
 * left alone, and a page that cannot be read is noted as failed.
 */
class IndexDocumentTextUseCase(
    private val documents: DocumentsRepository,
    private val pages: PagesRepository,
    private val storage: DocumentStorage,
    private val content: PageContentProvider,
) {
    suspend operator fun invoke(documentUuid: String) {
        val document =
            runCatching { documents.getDocument(documentUuid) }.getOrNull() as? Document.Managed
                ?: return
        // Not found by search while it is there. If it comes back, it is read then.
        if (document.trashedAtEpochMillis != null) return

        // A document that came from before pages were counted has none to read yet.
        if (document.pageCount == null) {
            val counted = storage.pageCount(document.filePath) ?: return
            if (!pages.createPages(documentUuid, counted)) return
        }

        val pending = pages.pagesToRead(documentUuid)
        if (pending.isEmpty()) return

        content.open(DocumentSource(document.location.value)).use { session ->
            for (index in pending) {
                // Deleted while it was being read: its pages went with it.
                if (!read(session, documentUuid, index)) return
            }
        }
    }

    /**
     * Reads one page until it is read or has failed as often as it is tried.
     *
     * @return `false` when the page is no longer there to write to.
     */
    private suspend fun read(
        session: PageContentSession,
        documentUuid: String,
        index: Int,
    ): Boolean {
        while (true) {
            val result =
                try {
                    session.page(index)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    PageContentResult.Failed(failure)
                }

            val text = (result as? PageContentResult.Available)?.text?.takeUnless { it.isBlank }
            return when {
                text != null ->
                    pages.storeText(documentUuid, index, text.toRecord(), EXTRACTOR_VERSION)
                result is PageContentResult.Failed ->
                    when (pages.recordFailure(documentUuid, index, MAX_ATTEMPTS)) {
                        PageTextStatus.PENDING -> continue
                        null -> false
                        else -> true
                    }
                else ->
                    pages.storeWithoutText(
                        documentUuid,
                        index,
                        PageTextStatus.OCR_DISABLED,
                        EXTRACTOR_VERSION,
                    )
            }
        }
    }

    private fun PageText.toRecord() =
        PageTextRecord(
            text = toPlainText(),
            origin = origin,
            confidence = confidence,
            engine = if (origin == ContentOrigin.EMBEDDED) PLATFORM_ENGINE else null,
        )

    /** In reading order, a line to a line. A blank line stays: it separates paragraphs. */
    private fun PageText.toPlainText(): String =
        lines.joinToString("\n") { line -> line.words.joinToString(" ") { it.text } }.trim()

    companion object {
        /**
         * The version of how text is read and written down. Raising it makes every page read by an
         * older one be read again, the next time the app starts.
         */
        const val EXTRACTOR_VERSION = 1

        /** How many times in a row reading a page may fail before it is given up on. */
        const val MAX_ATTEMPTS = 3

        /** What reads a PDF's own text layer. */
        const val PLATFORM_ENGINE = "platform"
    }
}

/**
 * Picks up reading where it was left, as the app starts: queues every document of the library with
 * pages still to be read. That covers a library just brought over from an older version of the
 * catalogue, a document whose reading never got to run, and a newer extractor.
 *
 * Pages that failed are tried again: what failed them, such as a file out of reach, may be over.
 */
class ResumeTextIndexingUseCase(
    private val pages: PagesRepository,
    private val queue: DocumentIndexQueue,
) {
    suspend operator fun invoke() {
        pages.requeue(IndexDocumentTextUseCase.EXTRACTOR_VERSION)
        pages.documentsToRead().forEach(queue::enqueue)
    }
}
