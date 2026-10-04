/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.repository

import com.bobbyesp.docucraft.feature.docscanner.data.db.dao.ActivityDao
import com.bobbyesp.docucraft.feature.docscanner.data.mapper.toModel
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentLocations
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentAvailability
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ReadingPosition
import com.bobbyesp.docucraft.feature.docscanner.domain.model.RecentDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentActivityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** @param now The clock, in epoch milliseconds. A parameter so that a test can hold it still. */
class DocumentActivityRepositoryImpl(
    private val activityDao: ActivityDao,
    private val locations: DocumentLocations,
    private val now: () -> Long = System::currentTimeMillis,
) : DocumentActivityRepository {

    override fun observeRecents(limit: Int): Flow<List<RecentDocument>> =
        activityDao
            .observeRecents(limit)
            .map { rows ->
                rows.map { row ->
                    RecentDocument(
                        document = row.document.toModel(locations),
                        lastOpenedAtEpochMillis = row.lastOpenedAt,
                        availability = row.availability,
                    )
                }
            }
            // The table is also written as a document is read, and none of those writes changes
            // what Recents shows.
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)

    override fun observeNotFound(): Flow<Set<String>> =
        activityDao.observeKeptNotFound().map { it.toSet() }.distinctUntilChanged()

    override suspend fun recordOpened(uuid: String) {
        activityDao.recordOpened(uuid, at = now())
    }

    override suspend fun recordAvailability(uuid: String, availability: DocumentAvailability) {
        activityDao.recordAvailability(uuid, availability, at = now())
    }

    override suspend fun readingPosition(uuid: String): ReadingPosition? {
        val row = activityDao.readingPositionOf(uuid) ?: return null
        val page = row.page ?: return null
        return ReadingPosition(page, row.offset ?: 0f).within(row.pageCount)
    }

    override suspend fun rememberReadingPosition(uuid: String, position: ReadingPosition) {
        // Checked on the way in as well: whatever is kept is a position, whoever reads it.
        val kept = position.within(pageCount = null)
        activityDao.setReadingPosition(uuid, page = kept.pageIndex, offset = kept.offset)
    }

    override suspend fun forgetReadingPositions() {
        activityDao.clearReadingPositions()
    }
}
