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
 * **The document's own text first.** [embedded] reads the text layer of the PDF. Only a page it
 * finds no text on, or cannot look at on this device, goes to [recognized], and only if the user
 * turned text recognition on for the document. Otherwise the page is noted as waiting for it.
 *
 * Nothing here is an error for the caller: a document that is gone, in the bin or another app's is
 * left alone, and a page that cannot be read is noted as failed.
 *
 * @param recognized Text recognition; `null` on a build that has none.
 */
class IndexDocumentTextUseCase(
    private val documents: DocumentsRepository,
    private val pages: PagesRepository,
    private val storage: DocumentStorage,
    private val embedded: PageContentProvider,
    private val recognized: PageContentProvider?,
) {
    suspend operator fun invoke(documentUuid: String) {
        // Asked again after each pass: text recognition may be turned on while the document is
        // being read, which makes pages pending that were not when the pass began.
        while (true) {
            val document = managed(documentUuid) ?: return
            // Not found by search while it is there. If it comes back, it is read then.
            if (document.trashedAtEpochMillis != null) return

            // A document that came from before pages were counted has none to read yet.
            if (document.pageCount == null) {
                val counted = storage.pageCount(document.filePath) ?: return
                if (!pages.createPages(documentUuid, counted)) return
            }

            val pending = pages.pagesToRead(documentUuid)
            if (pending.isEmpty()) return

            Reading(document).use { reading ->
                for (index in pending) {
                    // Deleted while it was being read: its pages went with it.
                    if (!reading.read(index)) return
                }
            }
        }
    }

    private suspend fun managed(documentUuid: String): Document.Managed? =
        runCatching { documents.getDocument(documentUuid) }.getOrNull() as? Document.Managed

    /** One pass over a document: its sessions, opened when first needed and closed together. */
    private inner class Reading(private val document: Document.Managed) : AutoCloseable {
        private val source = DocumentSource(document.location.value)
        private var own: PageContentSession? = null
        private var recognition: PageContentSession? = null

        /**
         * Reads one page until it is read or has failed as often as it is tried.
         *
         * @return `false` when the page is no longer there to write to.
         */
        suspend fun read(index: Int): Boolean {
            while (true) {
                val result = attempt { own().page(index) }
                val stored =
                    when {
                        result.hasText -> store(index, result.text())
                        result is PageContentResult.Failed -> null
                        else -> recognize(index)
                    }
                if (stored != null) return stored

                when (pages.recordFailure(document.uuid, index, MAX_ATTEMPTS)) {
                    PageTextStatus.PENDING -> continue
                    null -> return false
                    else -> return true
                }
            }
        }

        /**
         * A page with no text of its own.
         *
         * @return Whether the page was there to write to; `null` when recognizing it failed.
         */
        private suspend fun recognize(index: Int): Boolean? {
            val recognizer = recognized
            // Asked of the catalogue now, not of what was read when the pass began: it is turned
            // on from the document's actions, perhaps while the document is being read.
            val wanted = managed(document.uuid)?.ocrEnabled ?: return false
            if (recognizer == null || !wanted) {
                return withoutText(index, PageTextStatus.OCR_DISABLED)
            }
            val result = attempt {
                (recognition ?: recognizer.open(source).also { recognition = it }).page(index)
            }
            return when {
                result.hasText -> store(index, result.text())
                result is PageContentResult.Failed -> null
                // Asked to look, it could not on this device: the page is as it was.
                result is PageContentResult.Unsupported ->
                    withoutText(index, PageTextStatus.OCR_DISABLED)
                else -> withoutText(index, PageTextStatus.NO_TEXT)
            }
        }

        private suspend fun own(): PageContentSession =
            own ?: embedded.open(source).also { own = it }

        private suspend fun store(index: Int, text: PageText): Boolean =
            pages.storeText(document.uuid, index, text.toRecord(), EXTRACTOR_VERSION)

        private suspend fun withoutText(index: Int, status: PageTextStatus): Boolean =
            pages.storeWithoutText(document.uuid, index, status, EXTRACTOR_VERSION)

        override fun close() {
            own?.close()
            recognition?.close()
        }
    }

    /** A provider is asked not to throw. One that does has failed to read the page. */
    private suspend fun attempt(read: suspend () -> PageContentResult): PageContentResult =
        try {
            read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            PageContentResult.Failed(failure)
        }

    private val PageContentResult.hasText: Boolean
        get() = this is PageContentResult.Available && text?.isBlank == false

    private fun PageContentResult.text(): PageText =
        checkNotNull((this as PageContentResult.Available).text)

    private fun PageText.toRecord(): PageTextRecord {
        val isRecognized = origin == ContentOrigin.RECOGNIZED
        return PageTextRecord(
            text = toPlainText(),
            origin = origin,
            confidence = confidence.takeIf { isRecognized },
            engine = engine ?: PLATFORM_ENGINE.takeUnless { isRecognized },
            language = language,
            // A PDF's own text is read from the PDF whenever it is needed. Recognized text costs
            // a recognition to get back, so where its words are is kept with it.
            layout = lines.takeIf { isRecognized },
        )
    }

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
 * Pages that failed are tried again: what failed them, such as a file out of reach or a recognition
 * model that had not arrived yet, may be over.
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

/**
 * Turns text recognition on or off for one document, which is the user's to choose: it is what lets
 * a scan be found by what it says, and it takes time and battery.
 *
 * Turning it on queues the document, so the pages that were waiting are read. Turning it off
 * forgets what was recognized, at once: those pages stop being found by their content.
 */
class SetDocumentTextRecognitionUseCase(
    private val pages: PagesRepository,
    private val queue: DocumentIndexQueue,
) {
    /** @return `false` when there is no such document in the library. */
    suspend operator fun invoke(documentUuid: String, enabled: Boolean): Boolean {
        if (!pages.setTextRecognition(documentUuid, enabled)) return false
        // If it cannot be queued here, it is when the app starts.
        if (enabled) runCatching { queue.enqueue(documentUuid) }
        return true
    }
}
