/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * The full-text index over what a document is called and described as. It holds no text of its own:
 * it points at `documents`, and Room keeps the two in step with triggers.
 *
 * The `unicode61` tokenizer is what makes "cancion" find "Canción": it folds case and removes
 * diacritics, for the index and for the query alike. The default tokenizer keeps them.
 *
 * The order of the columns is the order search ranks them in, most telling first.
 */
@Fts4(contentEntity = DocumentEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "documents_fts")
data class DocumentFtsEntity(
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "original_name") val originalName: String,
    @ColumnInfo(name = "suggested_title") val suggestedTitle: String?,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "pdf_author") val pdfAuthor: String?,
    @ColumnInfo(name = "pdf_subject") val pdfSubject: String?,
    @ColumnInfo(name = "pdf_keywords") val pdfKeywords: String?,
)

/** The full-text index over the text of the pages, pointing at `page_texts`. */
@Fts4(contentEntity = PageTextEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "page_texts_fts")
data class PageTextFtsEntity(@ColumnInfo(name = "text") val text: String)
