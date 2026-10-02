/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.details

import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase.DocumentText
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.scanner.ContentRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest

/**
 * What the details of an open document show. Any of it may be unknown for a document from another
 * app, which the catalogue has never seen.
 *
 * @param text Whether it has text to select; `null` while that is still being looked into.
 */
data class ViewerDocumentDetails(
    val name: String,
    val description: String?,
    val pageCount: Int?,
    val sizeBytes: Long?,
    val text: DocumentText? = null,
)

/** Reads what can be learnt about a document from the file itself. */
fun interface DocumentFactsReader {
    suspend fun read(document: ContentRef): DocumentFacts
}

data class DocumentFacts(val sizeBytes: Long?, val pageCount: Int?)

/**
 * The details of the document a viewer shows, emitting `null` once it is gone.
 *
 * A catalogued document's come from the catalogue, which already knows its size and page count; the
 * viewer used to recompute the size through the content resolver from a composable, and to take the
 * page count from the rendering engine. An external document's are read from the file, once.
 *
 * Whether the document has text takes reading some of its pages, so the details come out at once
 * without it, and again with it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveViewerDocumentDetailsUseCase(
    private val observeDocument: ObserveDocumentUseCase,
    private val facts: DocumentFactsReader,
    private val detectText: suspend (DocumentSource) -> DocumentText,
) {
    operator fun invoke(ref: ViewerDocumentRef): Flow<ViewerDocumentDetails?> =
        when (ref) {
            is ViewerDocumentRef.Catalogued ->
                observeDocument(ref.uuid).transformLatest { document ->
                    if (document == null) {
                        emit(null)
                        return@transformLatest
                    }
                    val details =
                        ViewerDocumentDetails(
                            name = document.name,
                            description = document.description,
                            pageCount = document.pageCount,
                            sizeBytes = document.sizeBytes,
                        )
                    emit(details)
                    emit(details.copy(text = detectText(DocumentSource(document.location.value))))
                }

            is ViewerDocumentRef.External ->
                flow {
                    val read = facts.read(ContentRef(ref.uri))
                    val details =
                        ViewerDocumentDetails(
                            name = ref.displayName,
                            description = null,
                            pageCount = read.pageCount,
                            sizeBytes = read.sizeBytes,
                        )
                    emit(details)
                    emit(details.copy(text = detectText(DocumentSource(ref.uri))))
                }
        }
}
