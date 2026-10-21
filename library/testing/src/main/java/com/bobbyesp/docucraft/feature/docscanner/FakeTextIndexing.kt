/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner

import com.bobbyesp.docucraft.feature.docscanner.domain.indexing.DocumentIndexQueue
import com.bobbyesp.docucraft.feature.docscanner.domain.model.PageTextStatus
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentTextStatus
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.Page
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.PageTextRecord
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.PagesRepository
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.PageText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A queue that only remembers what it was asked. */
class FakeDocumentIndexQueue : DocumentIndexQueue {

    /** The documents queued, in order. */
    val queued = mutableListOf<String>()

    var failure: Exception? = null

    override fun enqueue(documentUuid: String) {
        failure?.let { throw it }
        queued += documentUuid
    }
}

/** The pages of documents, kept in memory, with the rules the real ones follow. */
class FakePagesRepository : PagesRepository {

    /** The pages of each document, by index. A document without an entry has none. */
    val pages = mutableMapOf<String, MutableMap<Int, Page>>()

    /** The text written down for each page. */
    val texts = mutableMapOf<Pair<String, Int>, PageTextRecord>()

    /** The extractor version each page was last read with. */
    val versions = mutableMapOf<Pair<String, Int>, Int>()

    /** The documents whose pages were never counted. */
    val uncounted = mutableSetOf<String>()

    /** The versions [requeue] was asked with, in order. */
    val requeued = mutableListOf<Int>()

    /** Gives [documentUuid] [count] pages, all still to be read. */
    fun pending(documentUuid: String, count: Int) {
        pages[documentUuid] =
            (0 until count)
                .associateWith { Page(documentUuid, it, PageTextStatus.PENDING, null, 0) }
                .toMutableMap()
    }

    fun statusOf(documentUuid: String): List<PageTextStatus> =
        pages[documentUuid].orEmpty().values.sortedBy { it.index }.map { it.textStatus }

    override suspend fun pagesOf(documentUuid: String): List<Page> =
        pages[documentUuid].orEmpty().values.sortedBy { it.index }

    override fun observeTextStatus(documentUuid: String): Flow<DocumentTextStatus?> = flowOf(null)

    override suspend fun textOf(documentUuid: String): List<String> =
        texts
            .filterKeys { it.first == documentUuid }
            .toSortedMap(compareBy { it.second })
            .values
            .map {
                it.text
            }

    override suspend fun pagesToRead(documentUuid: String): List<Int> =
        pagesOf(documentUuid).filter { it.textStatus == PageTextStatus.PENDING }.map { it.index }

    override suspend fun documentsToRead(): List<String> =
        (uncounted +
                pages
                    .filterValues { all ->
                        all.values.any { it.textStatus == PageTextStatus.PENDING }
                    }
                    .keys)
            .toList()

    override suspend fun createPages(documentUuid: String, count: Int): Boolean {
        if (count < 1 || !uncounted.remove(documentUuid)) return false
        pending(documentUuid, count)
        return true
    }

    override suspend fun storeText(
        documentUuid: String,
        pageIndex: Int,
        text: PageTextRecord,
        extractorVersion: Int,
    ): Boolean {
        val page = pages[documentUuid]?.get(pageIndex) ?: return false
        pages.getValue(documentUuid)[pageIndex] =
            page.copy(
                textStatus = PageTextStatus.EXTRACTED,
                textOrigin = text.origin,
                attempts = 0,
            )
        texts[documentUuid to pageIndex] = text
        versions[documentUuid to pageIndex] = extractorVersion
        return true
    }

    override suspend fun storeWithoutText(
        documentUuid: String,
        pageIndex: Int,
        status: PageTextStatus,
        extractorVersion: Int,
    ): Boolean {
        val page = pages[documentUuid]?.get(pageIndex) ?: return false
        pages.getValue(documentUuid)[pageIndex] =
            page.copy(textStatus = status, textOrigin = null, attempts = 0)
        texts -= documentUuid to pageIndex
        versions[documentUuid to pageIndex] = extractorVersion
        return true
    }

    override suspend fun recordFailure(
        documentUuid: String,
        pageIndex: Int,
        maxAttempts: Int,
    ): PageTextStatus? {
        val page = pages[documentUuid]?.get(pageIndex) ?: return null
        val attempts = page.attempts + 1
        val status = if (attempts >= maxAttempts) PageTextStatus.FAILED else PageTextStatus.PENDING
        pages.getValue(documentUuid)[pageIndex] =
            page.copy(textStatus = status, attempts = attempts)
        return status
    }

    /** Whether text recognition is on for each document it was set for. */
    val recognition = mutableMapOf<String, Boolean>()

    override suspend fun recognizedText(documentUuid: String, pageIndex: Int): PageText? {
        val record = texts[documentUuid to pageIndex] ?: return null
        val layout = record.layout ?: return null
        return PageText(layout, ContentOrigin.RECOGNIZED, record.confidence, record.engine)
    }

    override suspend fun setTextRecognition(documentUuid: String, enabled: Boolean): Boolean {
        val all = pages[documentUuid] ?: return false
        recognition[documentUuid] = enabled
        all.replaceAll { index, page ->
            when {
                enabled && page.textStatus == PageTextStatus.OCR_DISABLED ->
                    page.copy(textStatus = PageTextStatus.PENDING)
                !enabled &&
                    (page.textOrigin == ContentOrigin.RECOGNIZED ||
                        page.textStatus == PageTextStatus.NO_TEXT) -> {
                    texts -= documentUuid to index
                    page.copy(textStatus = PageTextStatus.OCR_DISABLED, textOrigin = null)
                }
                else -> page
            }
        }
        return true
    }

    override suspend fun requeue(extractorVersion: Int) {
        requeued += extractorVersion
        pages.values.forEach { all ->
            all.replaceAll { index, page ->
                val version = versions[page.documentUuid to index]
                val stale =
                    page.textStatus != PageTextStatus.PENDING &&
                        version != null &&
                        version < extractorVersion
                if (page.textStatus == PageTextStatus.FAILED || stale) {
                    page.copy(textStatus = PageTextStatus.PENDING, attempts = 0)
                } else page
            }
        }
    }
}
