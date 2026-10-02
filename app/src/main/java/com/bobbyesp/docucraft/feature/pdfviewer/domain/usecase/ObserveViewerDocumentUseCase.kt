/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ObserveDocumentUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Follows the document a viewer shows, emitting `null` once it is gone.
 *
 * A catalogued document is followed rather than read once: on expanded windows the list stays
 * beside the viewer, so it can be renamed or deleted while open. An external one cannot change
 * under the viewer, so it is emitted once.
 */
class ObserveViewerDocumentUseCase(private val observeDocument: ObserveDocumentUseCase) {

    operator fun invoke(ref: ViewerDocumentRef): Flow<BasicDocument?> =
        when (ref) {
            is ViewerDocumentRef.Catalogued ->
                observeDocument(ref.uuid).map { it?.toBasicDocument() }

            // Keyed by its location: the only identity it has that stays the same between opens.
            is ViewerDocumentRef.External ->
                flowOf(
                    BasicDocument(
                        uuid = ref.uri,
                        filename = ref.displayName,
                        uri = ref.uri,
                        title = ref.displayName,
                    )
                )
        }
}

private fun Document.toBasicDocument() =
    BasicDocument(
        uuid = uuid,
        filename = filename,
        uri = location.value,
        title = title,
        description = description,
    )
