/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.suggestions

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.PagesRepository
import kotlinx.coroutines.flow.first

/**
 * What can be proposed for a document from what it says: how to call it, how to describe it, and
 * where to put it. Each part is there or not on its own; a proposal is never applied by itself, the
 * user takes the parts they want.
 *
 * @property folderUuid A folder the library already has.
 * @property tagNames Names of tags, existing or not: a tag is found or created by its name.
 */
data class DocumentSuggestions(
    val title: String? = null,
    val description: String? = null,
    val folderUuid: String? = null,
    val tagNames: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = title == null && description == null && folderUuid == null && tagNames.isEmpty()
}

/**
 * What a document is described from.
 *
 * @property pages The text of its pages that have any, in reading order.
 */
data class SuggestionRequest(
    val documentUuid: String,
    val originalName: String,
    val pages: List<String>,
)

/**
 * Whatever proposes details for a document from its text. No build has one yet: this is where one
 * goes, bound in `ScannedDocumentModule`, and everything around it is already in place.
 *
 * It only ever sees text the document's pages gave, so it runs after they are read, and never for a
 * document without text.
 */
fun interface DocumentSuggester {
    suspend fun suggest(request: SuggestionRequest): DocumentSuggestions
}

/** How asking for suggestions ended. Each is an answer the screen has something to say about. */
sealed interface SuggestionOutcome {
    /** This build has nothing that makes suggestions, so there is nothing to say either. */
    data object NotAvailable : SuggestionOutcome

    /**
     * The document has no text, and never will while text recognition is off for it: a scan is
     * images, and its text only comes from recognizing them. Turning it on is the user's choice.
     */
    data object TextRecognitionOff : SuggestionOutcome

    /** Its pages were read and say nothing. */
    data object NoText : SuggestionOutcome

    /** Possibly an empty proposal: the text was there, and nothing came of it. */
    data class Suggested(val suggestions: DocumentSuggestions) : SuggestionOutcome

    data object Failed : SuggestionOutcome
}

/**
 * Proposes details for a document of the library.
 *
 * The order is the rule: the text first. It waits for the document's pages to be read, which the
 * background reading does, recognition included when the document has it on; only then is there
 * anything to propose from.
 *
 * @param suggester What makes the proposals, or `null` on a build that has none.
 */
class SuggestDocumentDetailsUseCase(
    private val suggester: DocumentSuggester?,
    private val documents: DocumentsRepository,
    private val pages: PagesRepository,
) {
    /** Whether asking can give anything at all. A screen with nothing to offer says nothing. */
    val isAvailable: Boolean
        get() = suggester != null

    suspend operator fun invoke(documentUuid: String): SuggestionOutcome {
        val suggester = suggester ?: return SuggestionOutcome.NotAvailable
        val document =
            runCatching { documents.getDocument(documentUuid) }.getOrNull() as? Document.Managed
                ?: return SuggestionOutcome.NotAvailable

        // Until nothing is left to read. A document whose pages were never counted has no status,
        // and nothing to wait for.
        pages.observeTextStatus(documentUuid).first { it == null || it.isRead }

        val text = pages.textOf(documentUuid).filter { it.isNotBlank() }
        if (text.isEmpty()) {
            return if (document.ocrEnabled) SuggestionOutcome.NoText
            else SuggestionOutcome.TextRecognitionOff
        }

        return runCatching {
                suggester.suggest(
                    SuggestionRequest(
                        documentUuid = documentUuid,
                        originalName = document.originalName,
                        pages = text,
                    )
                )
            }
            .fold(
                onSuccess = { SuggestionOutcome.Suggested(it) },
                onFailure = { SuggestionOutcome.Failed },
            )
    }
}
