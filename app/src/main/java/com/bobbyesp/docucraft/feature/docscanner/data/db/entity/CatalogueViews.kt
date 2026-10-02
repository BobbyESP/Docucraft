/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Embedded

/**
 * The documents of the library: the ones the app keeps that are not in the bin.
 *
 * A view, so the two conditions are written once. Repeated in every query of the library, sooner or
 * later one of them would be forgotten, and the bin or another app's documents would show up in a
 * list or a search.
 */
@DatabaseView(
    viewName = "library_documents",
    value = "SELECT * FROM documents WHERE custody = 'MANAGED' AND trashed_at IS NULL",
)
data class LibraryDocumentView(@Embedded val document: DocumentEntity)

/**
 * How far reading a document's text has got, counted from its pages. A document's indexing state is
 * not stored anywhere: this is it.
 *
 * @property recognized Pages whose text came from text recognition.
 */
@DatabaseView(
    viewName = "document_text_status",
    // `IS` rather than `=`: a page without text has no origin, and `NULL = 'RECOGNIZED'` is NULL,
    // which would make the whole sum NULL.
    value =
        "SELECT document_id, COUNT(*) AS pages, " +
            "SUM(text_status = 'PENDING') AS pending, " +
            "SUM(text_status = 'FAILED') AS failed, " +
            "SUM(text_origin IS 'RECOGNIZED') AS recognized " +
            "FROM pages GROUP BY document_id",
)
data class DocumentTextStatusView(
    @ColumnInfo(name = "document_id") val documentId: Long,
    @ColumnInfo(name = "pages") val pages: Int,
    @ColumnInfo(name = "pending") val pending: Int,
    @ColumnInfo(name = "failed") val failed: Int,
    @ColumnInfo(name = "recognized") val recognized: Int,
)
