/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.data.content

import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.documentcontent.PageContentProvider
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.PageContentSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The document's own text first and, page by page, [recognized] text where it has none. That covers
 * a document mixing typed and scanned pages, and, with text recognition, devices whose platform
 * cannot read the text layer (D1).
 *
 * [recognized] is only opened once a page needs it: recognition is costly, and most documents never
 * will.
 *
 * @param recognized Text recognition; `null` until there is one.
 */
class LayeredPageContentProvider(
    private val embedded: PageContentProvider,
    private val recognized: PageContentProvider?,
) : PageContentProvider {

    override val origin: ContentOrigin
        get() = embedded.origin

    override suspend fun open(document: DocumentSource): PageContentSession {
        val embeddedSession = embedded.open(document)
        val fallback = recognized ?: return embeddedSession
        return LayeredSession(embeddedSession) { fallback.open(document) }
    }
}

private class LayeredSession(
    private val embedded: PageContentSession,
    private val openRecognized: suspend () -> PageContentSession,
) : PageContentSession {

    private val opening = Mutex()
    @Volatile private var recognized: PageContentSession? = null
    @Volatile private var closed = false

    override suspend fun page(index: Int): PageContentResult {
        val own = embedded.page(index)
        if (!own.lacksText) return own
        return merge(own, recognized().page(index))
    }

    /**
     * Recognized text, with the links only the document itself knows about. When recognition has
     * nothing better to say, the document's own answer stands, except that recognition finding no
     * text is truer than the platform being unable to look.
     */
    private fun merge(own: PageContentResult, other: PageContentResult): PageContentResult =
        when (other) {
            is PageContentResult.Available ->
                if (own is PageContentResult.Available) other.copy(links = own.links + other.links)
                else other
            PageContentResult.NoText ->
                if (own is PageContentResult.Available) own else PageContentResult.NoText
            PageContentResult.Unsupported,
            is PageContentResult.Failed -> own
        }

    private suspend fun recognized(): PageContentSession =
        recognized
            ?: opening.withLock {
                recognized
                    ?: openRecognized().also { session ->
                        recognized = session
                        // Closed while it was opening: nobody else will close it.
                        if (closed) session.close()
                    }
            }

    override fun close() {
        closed = true
        embedded.close()
        recognized?.close()
    }
}

/** Whether recognition could add text: the page has none, or the platform could not look. */
private val PageContentResult.lacksText: Boolean
    get() =
        when (this) {
            PageContentResult.NoText,
            PageContentResult.Unsupported -> true
            is PageContentResult.Available -> text == null
            is PageContentResult.Failed -> false
        }
