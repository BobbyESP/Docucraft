/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentAvailability
import kotlinx.coroutines.flow.Flow

/** A document together with the part of its activity that Recents shows. */
data class RecentDocumentRow(
    @Embedded val document: DocumentEntity,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long?,
    @ColumnInfo(name = "availability") val availability: DocumentAvailability,
)

/**
 * Where a document was left, with what is needed to tell whether that place is still in it.
 *
 * @property page `null` when no position is kept.
 */
data class ReadingPositionRow(
    @ColumnInfo(name = "reading_page") val page: Int?,
    @ColumnInfo(name = "reading_offset") val offset: Float?,
    @ColumnInfo(name = "page_count") val pageCount: Int?,
)

/**
 * `document_activity`: what changes about a document as it is used. Every write finds its row by
 * the document's uuid, which is all that travels above the data layer.
 */
@Dao
interface ActivityDao {

    /**
     * Recents: the app's own documents outside the bin, and the linked ones, by their last
     * activity. The row id breaks a tie, so that two documents saved in the same millisecond do not
     * swap places between two readings.
     *
     * The same content is listed once. A PDF opened from another app arrives at a new location each
     * time, and is a new linked document each time: without this, opening it twice, or opening it
     * again after saving it, showed it twice. A linked document is left out when the library has
     * that content, or when another linked document with it was used later. The one that stays
     * takes the place of the ones it stands for, so that what was just read is still first. Two
     * documents of the library are never merged: a second copy was asked for.
     *
     * A linked document has no hash until it has been read once, and is listed until then.
     */
    @Query(
        """SELECT d.*, a.last_opened_at, a.availability
        FROM documents d JOIN document_activity a ON a.document_id = d.id
        WHERE d.trashed_at IS NULL
          AND NOT (d.custody = 'LINKED' AND d.content_hash IS NOT NULL AND EXISTS (
            SELECT 1 FROM documents o JOIN document_activity oa ON oa.document_id = o.id
            WHERE o.content_hash = d.content_hash AND o.id <> d.id AND o.trashed_at IS NULL
              AND (o.custody = 'MANAGED'
                OR oa.last_activity_at > a.last_activity_at
                OR (oa.last_activity_at = a.last_activity_at AND o.id > d.id))))
        ORDER BY MAX(a.last_activity_at, COALESCE((
            SELECT MAX(oa.last_activity_at)
            FROM documents o JOIN document_activity oa ON oa.document_id = o.id
            WHERE o.content_hash = d.content_hash AND o.id <> d.id AND o.custody = 'LINKED'), 0)
          ) DESC, d.id DESC
        LIMIT :limit"""
    )
    fun observeRecents(limit: Int): Flow<List<RecentDocumentRow>>

    /**
     * The last activity is the later of when the document entered the catalogue and when it was
     * last opened. Both are written here, in one statement, so that neither is ever seen without
     * the other.
     *
     * @return How many documents were noted: none when there is no document with this [uuid].
     */
    @Query(
        """UPDATE document_activity
        SET last_opened_at = :at,
            last_activity_at = MAX(
                :at, (SELECT created_at FROM documents WHERE id = document_activity.document_id))
        WHERE document_id = (SELECT id FROM documents WHERE uuid = :uuid)"""
    )
    suspend fun recordOpened(uuid: String, at: Long): Int

    /** The documents the app keeps whose file was not there the last time it was looked for. */
    @Query(
        """SELECT d.uuid FROM documents d JOIN document_activity a ON a.document_id = d.id
        WHERE d.custody = 'MANAGED' AND a.availability = 'NOT_FOUND'"""
    )
    fun observeKeptNotFound(): Flow<List<String>>

    @Query(
        """UPDATE document_activity
        SET availability = :availability, availability_checked_at = :at
        WHERE document_id = (SELECT id FROM documents WHERE uuid = :uuid)"""
    )
    suspend fun recordAvailability(uuid: String, availability: DocumentAvailability, at: Long): Int

    @Query(
        """SELECT a.reading_page, a.reading_offset, d.page_count
        FROM document_activity a JOIN documents d ON d.id = a.document_id
        WHERE d.uuid = :uuid"""
    )
    suspend fun readingPositionOf(uuid: String): ReadingPositionRow?

    @Query(
        """UPDATE document_activity
        SET reading_page = :page, reading_offset = :offset
        WHERE document_id = (SELECT id FROM documents WHERE uuid = :uuid)"""
    )
    suspend fun setReadingPosition(uuid: String, page: Int, offset: Float): Int

    /** Only the rows that have one, so that forgetting nothing writes nothing. */
    @Query(
        """UPDATE document_activity
        SET reading_page = NULL, reading_offset = NULL
        WHERE reading_page IS NOT NULL OR reading_offset IS NOT NULL"""
    )
    suspend fun clearReadingPositions(): Int
}
