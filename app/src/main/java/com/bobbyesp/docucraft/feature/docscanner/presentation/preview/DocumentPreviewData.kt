/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.preview

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentOrigin
import com.bobbyesp.scanner.ContentRef
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Documents for composable previews, which have no catalogue to read from. */
object DocumentPreviewData {

    /** A scanned document with only what a preview wants to vary. */
    fun document(
        uuid: String,
        title: String?,
        description: String?,
        createdAtEpochMillis: Long = 1_758_290_000_000,
        sizeBytes: Long? = 1024,
        pageCount: Int? = 10,
    ): Document.Managed =
        Document.Managed(
            uuid = uuid,
            originalName = "Scan_20260919_142530",
            title = title,
            suggestedTitle = null,
            description = description,
            location = ContentRef("content://com.example.documents/document/$uuid"),
            sizeBytes = sizeBytes,
            pageCount = pageCount,
            createdAtEpochMillis = createdAtEpochMillis,
            filePath = "documents/$uuid.pdf",
            origin = DocumentOrigin.SCAN,
            capturedAtEpochMillis = createdAtEpochMillis,
            contentUpdatedAtEpochMillis = createdAtEpochMillis,
            isFavorite = false,
            ocrEnabled = false,
            trashedAtEpochMillis = null,
        )

    val documents: ImmutableList<Document.Managed> =
        persistentListOf(
            document(
                uuid = "1asd",
                title = "Documento 1 de prueba. Título corto",
                description =
                    "Description para el documento 1. La descripción no va a ser muy larga.",
                sizeBytes = 1024,
                pageCount = 10,
            ),
            document(
                uuid = "2asd",
                title = "Apuntes de programación",
                description =
                    "Esta descripción va a sobrepasar el límite de caracteres para ver cómo se comporta el diseño. " +
                        "Esto es una prueba para ver cómo se comporta el diseño en caso de que la descripción sea muy larga.",
                sizeBytes = 2048,
                pageCount = 20,
            ),
        )
}
