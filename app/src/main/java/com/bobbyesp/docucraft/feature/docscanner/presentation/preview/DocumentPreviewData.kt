/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.preview

import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import com.bobbyesp.scanner.ContentRef
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

object DocumentPreviewData {
    val documents: ImmutableList<ScannedDocument> =
        persistentListOf(
            ScannedDocument(
                uuid = "1asd",
                filename = "document1.pdf",
                title = "Documento 1 de prueba. Título corto",
                description =
                    "Description para el documento 1. La descripción no va a ser muy larga.",
                location = ContentRef("content://com.example.documents/document/1"),
                capturedAtEpochMillis = System.currentTimeMillis(),
                sizeBytes = 1024,
                pageCount = 10,
                thumbnail = ContentRef("content://com.example.thumbnails/thumbnail/1"),
            ),
            ScannedDocument(
                uuid = "2asd",
                filename = "document2.pdf",
                title = "Apuntes de programación",
                description =
                    "Esta descripción va a sobrepasar el límite de caracteres para ver cómo se comporta el diseño. " +
                        "Esto es una prueba para ver cómo se comporta el diseño en caso de que la descripción sea muy larga.",
                location = ContentRef("content://com.example.documents/document/2"),
                capturedAtEpochMillis = System.currentTimeMillis(),
                sizeBytes = 2048,
                pageCount = 20,
                thumbnail = ContentRef("content://com.example.thumbnails/thumbnail/2"),
            ),
        )
}
