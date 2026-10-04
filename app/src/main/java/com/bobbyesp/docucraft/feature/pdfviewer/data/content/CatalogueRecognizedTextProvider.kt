/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.data.content

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.PagesRepository
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.documentcontent.PageContentProvider
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.PageContentSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Recognized text for the viewer, as far as the user asked for it.
 *
 * Text recognition is something the user turns on for a document. So the viewer does not recognize
 * whatever page happens to be on screen: it shows what was already recognized and written down, and
 * only recognizes a page itself when its document has recognition on and the page has not been
 * reached yet by the reading that runs in the background.
 *
 * A document that is not in the library, another app's or one in the bin, has no recognized text:
 * every page answers [PageContentResult.Unsupported], which leaves the document's own answer
 * standing.
 *
 * @param recognition The engine, for the pages that have not been written down yet.
 */
class CatalogueRecognizedTextProvider(
    private val documents: DocumentsRepository,
    private val pages: PagesRepository,
    private val recognition: PageContentProvider,
) : PageContentProvider {

    override val origin: ContentOrigin = ContentOrigin.RECOGNIZED

    override suspend fun open(document: DocumentSource): PageContentSession {
        // By where it is: that is all a viewer says of the document it shows.
        val managed =
            runCatching {
                documents.observeDocuments().first().firstOrNull {
                    it.location.value == document.value
                }
            }
                .getOrNull() ?: return NoRecognizedText
        return CatalogueSession(managed, document)
    }

    private inner class CatalogueSession(
        private val document: Document.Managed,
        private val source: DocumentSource,
    ) : PageContentSession {
        private val opening = Mutex()
        @Volatile private var live: PageContentSession? = null
        @Volatile private var closed = false

        override suspend fun page(index: Int): PageContentResult {
            pages.recognizedText(document.uuid, index)?.let { stored ->
                return PageContentResult.Available(text = stored, links = emptyList())
            }
            if (!document.ocrEnabled) return PageContentResult.Unsupported
            return live().page(index)
        }

        private suspend fun live(): PageContentSession =
            live
                ?: opening.withLock {
                    live
                        ?: recognition.open(source).also { session ->
                            live = session
                            // Closed while it was opening: nobody else will close it.
                            if (closed) session.close()
                        }
                }

        override fun close() {
            closed = true
            live?.close()
        }
    }
}

private object NoRecognizedText : PageContentSession {
    override suspend fun page(index: Int): PageContentResult = PageContentResult.Unsupported

    override fun close() = Unit
}
