/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.details

import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.scanner.ContentRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * What the details of an open document show. Any of it may be unknown for a document from another
 * app, which the catalogue has never seen.
 */
data class ViewerDocumentDetails(
    val name: String,
    val description: String?,
    val pageCount: Int?,
    val sizeBytes: Long?,
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
 */
class ObserveViewerDocumentDetailsUseCase(
    private val observeDocument: ObserveDocumentUseCase,
    private val facts: DocumentFactsReader,
) {
    operator fun invoke(ref: ViewerDocumentRef): Flow<ViewerDocumentDetails?> =
        when (ref) {
            is ViewerDocumentRef.Catalogued ->
                observeDocument(ref.uuid).map { document ->
                    document?.let {
                        ViewerDocumentDetails(
                            name = it.title ?: it.filename,
                            description = it.description,
                            pageCount = it.pageCount.takeIf { count -> count > 0 },
                            sizeBytes = it.sizeBytes.takeIf { size -> size > 0 },
                        )
                    }
                }

            is ViewerDocumentRef.External ->
                flow {
                    val read = facts.read(ContentRef(ref.uri))
                    emit(
                        ViewerDocumentDetails(
                            name = ref.displayName,
                            description = null,
                            pageCount = read.pageCount,
                            sizeBytes = read.sizeBytes,
                        )
                    )
                }
        }
}
