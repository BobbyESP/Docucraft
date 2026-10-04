/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.repository

import com.bobbyesp.docucraft.feature.docscanner.domain.model.PageTextStatus
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.PageText
import com.bobbyesp.documentcontent.TextLine
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
 * @property language BCP 47 tag of its language, when it could be told.
 * @property layout Where each word is on the page. Kept for recognized text only, so that it can be
 *   selected without recognizing the page again; a PDF's own text is read from the PDF.
 */
data class PageTextRecord(
    val text: String,
    val origin: ContentOrigin,
    val confidence: Float?,
    val engine: String?,
    val language: String? = null,
    val layout: List<TextLine>? = null,
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
     * The text of a document's pages, in order, whichever way each was read. Only the pages that
     * have text: one that has none, or is still to be read, is not in the list.
     */
    suspend fun textOf(documentUuid: String): List<String>

    /**
     * The recognized text of a page with where each word is, as it was written down. `null` when
     * the page was not recognized, or is not there.
     */
    suspend fun recognizedText(documentUuid: String, pageIndex: Int): PageText?

    /**
     * Turns text recognition on or off for a document the app keeps.
     *
     * Turning it on makes the pages that were waiting for it pending again. Turning it off forgets
     * what was recognized, so those pages stop being found by it; the text a PDF has of its own
     * stays.
     *
     * @return `false` when there is no such document, or it is another app's.
     */
    suspend fun setTextRecognition(documentUuid: String, enabled: Boolean): Boolean

    /**
     * Makes pending again the pages that failed, and the ones read by an extractor older than
     * [extractorVersion]. Their text stays until they are read again.
     */
    suspend fun requeue(extractorVersion: Int)
}
