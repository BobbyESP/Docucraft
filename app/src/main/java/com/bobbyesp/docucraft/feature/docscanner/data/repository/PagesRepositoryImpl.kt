/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.repository

import com.bobbyesp.docucraft.feature.docscanner.data.db.PageLayoutCodec
import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.PageDao
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.PageLayoutEntity
import com.bobbyesp.docucraft.feature.docscanner.domain.model.PageTextStatus
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentTextStatus
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.Page
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.PageTextRecord
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.PagesRepository
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.PageText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** @param now The clock, in epoch milliseconds. A parameter so that a test can hold it still. */
class PagesRepositoryImpl(
    private val pageDao: PageDao,
    private val now: () -> Long = System::currentTimeMillis,
) : PagesRepository {

    override suspend fun pagesOf(documentUuid: String): List<Page> =
        pageDao.pagesOf(documentUuid).map { row ->
            Page(
                documentUuid = row.documentUuid,
                index = row.pageIndex,
                textStatus = row.textStatus,
                textOrigin = row.textOrigin,
                attempts = row.attempts,
            )
        }

    override fun observeTextStatus(documentUuid: String): Flow<DocumentTextStatus?> =
        pageDao.observeTextStatus(documentUuid).map { status ->
            status?.let {
                DocumentTextStatus(
                    pages = it.pages,
                    pending = it.pending,
                    failed = it.failed,
                    recognized = it.recognized,
                )
            }
        }

    override suspend fun pagesToRead(documentUuid: String): List<Int> =
        pageDao.pendingPagesOf(documentUuid)

    override suspend fun documentsToRead(): List<String> = pageDao.documentsWithPagesToRead()

    override suspend fun createPages(documentUuid: String, count: Int): Boolean {
        // A document the app keeps has at least one page, and the table refuses anything else.
        if (count < 1) return false
        return pageDao.createPages(documentUuid, count, at = now())
    }

    override suspend fun storeText(
        documentUuid: String,
        pageIndex: Int,
        text: PageTextRecord,
        extractorVersion: Int,
    ): Boolean =
        pageDao.storeText(
            documentUuid = documentUuid,
            pageIndex = pageIndex,
            text = text.text,
            origin = text.origin,
            confidence = text.confidence,
            engine = text.engine,
            language = text.language,
            layout =
                text.layout?.let {
                    // The page's own id is filled in where the page is found.
                    PageLayoutEntity(
                        pageId = 0,
                        formatVersion = PageLayoutCodec.VERSION,
                        data = PageLayoutCodec.encode(it),
                    )
                },
            extractorVersion = extractorVersion,
            at = now(),
        )

    override suspend fun recognizedText(documentUuid: String, pageIndex: Int): PageText? {
        val stored = pageDao.layoutOf(documentUuid, pageIndex) ?: return null
        val lines = PageLayoutCodec.decode(stored.formatVersion, stored.data) ?: return null
        return PageText(
            lines = lines,
            origin = ContentOrigin.RECOGNIZED,
            confidence = stored.confidence,
            engine = stored.engine,
            language = stored.language,
        )
    }

    override suspend fun setTextRecognition(documentUuid: String, enabled: Boolean): Boolean =
        pageDao.setTextRecognition(documentUuid, enabled, at = now())

    override suspend fun storeWithoutText(
        documentUuid: String,
        pageIndex: Int,
        status: PageTextStatus,
        extractorVersion: Int,
    ): Boolean {
        require(status == PageTextStatus.NO_TEXT || status == PageTextStatus.OCR_DISABLED) {
            "$status is not a way for a page to have been read without text"
        }
        return pageDao.storeWithoutText(documentUuid, pageIndex, status, extractorVersion, now())
    }

    override suspend fun recordFailure(
        documentUuid: String,
        pageIndex: Int,
        maxAttempts: Int,
    ): PageTextStatus? = pageDao.recordFailure(documentUuid, pageIndex, maxAttempts)

    override suspend fun requeue(extractorVersion: Int) {
        pageDao.requeue(extractorVersion)
    }
}
