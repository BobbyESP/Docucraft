/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.repository

import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.PageDao
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentTextStatus
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.Page
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.PagesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PagesRepositoryImpl(private val pageDao: PageDao) : PagesRepository {

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
}
