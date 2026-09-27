/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase

import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.documentcontent.PageContentProvider
import com.bobbyesp.documentcontent.PageContentResult

/** Whether a document has text to select, as the details say. */
enum class DocumentText {
    /** The document's own text layer. */
    Embedded,

    /** Text read from the page images. */
    Recognized,

    /** Images only, such as a camera scan. */
    None,

    /** This device cannot read text from PDFs (D1). */
    Unsupported,

    /** It could not be read. */
    Unknown,
}

/**
 * Looks at the first pages of a document for text. A few pages, not all of them: enough for a typed
 * document with a scanned cover, cheap enough for a 300-page one.
 */
class DetectDocumentTextUseCase(private val provider: PageContentProvider) {

    suspend operator fun invoke(document: DocumentSource): DocumentText =
        provider.open(document).use { session ->
            var sawNoText = false
            for (index in 0 until PagesLookedAt) {
                when (val page = session.page(index)) {
                    is PageContentResult.Available -> {
                        val text = page.text
                        if (text != null && !text.isBlank) {
                            return@use if (text.origin == ContentOrigin.RECOGNIZED) {
                                DocumentText.Recognized
                            } else {
                                DocumentText.Embedded
                            }
                        }
                        sawNoText = true
                    }
                    PageContentResult.NoText -> sawNoText = true
                    PageContentResult.Unsupported -> return@use DocumentText.Unsupported
                    // Past the last page, or a page that could not be read.
                    is PageContentResult.Failed -> break
                }
            }
            if (sawNoText) DocumentText.None else DocumentText.Unknown
        }

    private companion object {
        const val PagesLookedAt = 5
    }
}
