/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.repository

import com.bobbyesp.docucraft.feature.docscanner.domain.model.PageTextStatus
import com.bobbyesp.documentcontent.ContentOrigin
import kotlinx.coroutines.flow.Flow

/**
 * A page of a document the app keeps, and how far reading its text has got.
 *
 * @property index Zero-based.
 * @property textOrigin Where its text came from; `null` unless [textStatus] is `EXTRACTED`.
 * @property attempts How many times reading it has failed.
 */
data class Page(
    val documentUuid: String,
    val index: Int,
    val textStatus: PageTextStatus,
    val textOrigin: ContentOrigin?,
    val attempts: Int,
)

/**
 * How far reading a document's text has got, counted from its pages. It is not kept anywhere: it is
 * what the pages say at the moment.
 *
 * @property recognized Pages whose text came from text recognition.
 */
data class DocumentTextStatus(
    val pages: Int,
    val pending: Int,
    val failed: Int,
    val recognized: Int,
) {
    /** Nothing is waiting to be read, whether or not every page could be. */
    val isRead: Boolean
        get() = pending == 0
}

/**
 * The pages of the documents the app keeps. For now, only what can be read of them; writing a
 * page's text belongs to whatever reads it, and arrives with it.
 */
interface PagesRepository {

    /** The pages of a document, in order. Empty when its pages have not been counted yet. */
    suspend fun pagesOf(documentUuid: String): List<Page>

    /** Emits again as pages are read; `null` while the document has no pages, or is gone. */
    fun observeTextStatus(documentUuid: String): Flow<DocumentTextStatus?>
}
