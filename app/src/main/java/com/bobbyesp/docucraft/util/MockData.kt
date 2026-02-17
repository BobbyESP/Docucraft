package com.bobbyesp.docucraft.util

import androidx.core.net.toUri
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

object MockData {
    object Documents {
        val documentsList: ImmutableList<ScannedDocument> =
            persistentListOf(
                ScannedDocument(
                    id = "1",
                    filename = "document1.pdf",
                    title = "Documento 1 de prueba. Título corto",
                    description =
                        "Description para el documento 1. La descripción no va a ser muy larga.",
                    path = "content://com.example.documents/document/1".toUri(),
                    createdTimestamp = System.currentTimeMillis(),
                    fileSize = 1024,
                    pageCount = 10,
                    thumbnail = "content://com.example.thumbnails/thumbnail/1",
                ),
                ScannedDocument(
                    id = "2",
                    filename = "document2.pdf",
                    title = "Apuntes de programación",
                    description =
                        "Esta descripción va a sobrepasar el límite de caracteres para ver cómo se comporta el diseño. " +
                            "Esto es una prueba para ver cómo se comporta el diseño en caso de que la descripción sea muy larga.",
                    path = "content://com.example.documents/document/2".toUri(),
                    createdTimestamp = System.currentTimeMillis(),
                    fileSize = 2048,
                    pageCount = 20,
                    thumbnail = "content://com.example.thumbnails/thumbnail/2",
                ),
            )
    }
}
