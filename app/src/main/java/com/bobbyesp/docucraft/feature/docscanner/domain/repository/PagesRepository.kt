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
 * The text read from a page, as it is written down.
 *
 * @property text Plain, in reading order.
 * @property confidence How sure text recognition was, from 0 to 1. Recognized text only.
 * @property engine What read it, such as `platform`.
 */
data class PageTextRecord(
    val text: String,
    val origin: ContentOrigin,
    val confidence: Float?,
    val engine: String?,
)

/**
 * The pages of the documents the app keeps, and the text read from them.
 *
 * Every write is to one page and is whole: its text and how far reading it got change together, so
 * that a page is never found read without its text, or the other way round.
 */
interface PagesRepository {
    /** The pages of a document, in order. Empty when its pages have not been counted yet. */
    suspend fun pagesOf(documentUuid: String): List<Page>

    /** Emits again as pages are read; `null` while the document has no pages, or is gone. */
    fun observeTextStatus(documentUuid: String): Flow<DocumentTextStatus?>

    /** The indexes of the pages of a document that are still to be read, in order. */
    suspend fun pagesToRead(documentUuid: String): List<Int>

    /**
     * The documents of the library with something still to be read: a page that is pending, or
     * pages that were never counted. Newest first.
     */
    suspend fun documentsToRead(): List<String>

    /**
     * Gives a document whose pages were never counted its [count] pages, all still to be read.
     *
     * @return `false` when nothing was done: the document is gone, is not one the app keeps, has
     *   its pages already, or [count] is not a number of pages.
     */
    suspend fun createPages(documentUuid: String, count: Int): Boolean

    /**
     * Writes down the text of a page, which makes it findable.
     *
     * @param extractorVersion The version of what read it.
     * @return `false` when the page is not there: its document was deleted meanwhile.
     */
    suspend fun storeText(
        documentUuid: String,
        pageIndex: Int,
        text: PageTextRecord,
        extractorVersion: Int,
    ): Boolean

    /**
     * Notes that a page was read and has no text to keep, and why: [status]. Text it had before is
     * forgotten.
     *
     * @return `false` when the page is not there.
     */
    suspend fun storeWithoutText(
        documentUuid: String,
        pageIndex: Int,
        status: PageTextStatus,
        extractorVersion: Int,
    ): Boolean

    /**
     * Notes that reading a page failed once more. At [maxAttempts] it is given up on.
     *
     * @return How the page is left, still pending or failed; `null` when it is not there.
     */
    suspend fun recordFailure(
        documentUuid: String,
        pageIndex: Int,
        maxAttempts: Int,
    ): PageTextStatus?

    /**
     * Makes pending again the pages that failed, and the ones read by an extractor older than
     * [extractorVersion]. Their text stays until they are read again.
     */
    suspend fun requeue(extractorVersion: Int)
}
