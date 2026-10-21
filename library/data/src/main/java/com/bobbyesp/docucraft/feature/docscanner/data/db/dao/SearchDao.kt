/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

/**
 * The two full-text queries a search is made of. Both go through `library_documents`, which is what
 * keeps the bin and other apps' documents out of the results.
 *
 * Each row carries the raw `matchinfo` of its match, which `Bm25` turns into a score. Ordering by
 * relevance cannot be done in SQL: FTS4 has no ranking function.
 */
@Dao
interface SearchDao {

    /**
     * The documents whose name, titles, description or PDF metadata match.
     *
     * @param query An FTS4 `MATCH` expression, already built. See `Fts4Query`.
     */
    @Query(
        """
        SELECT d.uuid AS uuid,
               a.last_activity_at AS last_activity_at,
               matchinfo(documents_fts, 'pcnalx') AS match_info
        FROM documents_fts
        JOIN library_documents d ON d.id = documents_fts.rowid
        JOIN document_activity a ON a.document_id = d.id
        WHERE documents_fts MATCH :query
        """
    )
    suspend fun matchDocuments(query: String): List<DocumentMatch>

    /**
     * The pages whose text matches, each with the fragment around the match.
     *
     * @param start What to mark the beginning of each matching stretch of the fragment with.
     * @param end What to mark its end with.
     * @param ellipsis What to put where the fragment was cut from the page's text.
     * @param words About how many words the fragment should have.
     */
    @Query(
        """
        SELECT d.uuid AS uuid,
               a.last_activity_at AS last_activity_at,
               p.page_index AS page_index,
               snippet(page_texts_fts, :start, :end, :ellipsis, -1, :words) AS fragment,
               matchinfo(page_texts_fts, 'pcnalx') AS match_info
        FROM page_texts_fts
        JOIN pages p ON p.id = page_texts_fts.rowid
        JOIN library_documents d ON d.id = p.document_id
        JOIN document_activity a ON a.document_id = d.id
        WHERE page_texts_fts MATCH :query
        """
    )
    suspend fun matchPages(
        query: String,
        start: String,
        end: String,
        ellipsis: String,
        words: Int,
    ): List<PageMatch>
}

/** Not a data class: a match is read once, and an array would make comparing two wrong. */
class DocumentMatch(
    @ColumnInfo(name = "uuid") val uuid: String,
    @ColumnInfo(name = "last_activity_at") val lastActivityAt: Long,
    @ColumnInfo(name = "match_info") val matchInfo: ByteArray,
)

class PageMatch(
    @ColumnInfo(name = "uuid") val uuid: String,
    @ColumnInfo(name = "last_activity_at") val lastActivityAt: Long,
    @ColumnInfo(name = "page_index") val pageIndex: Int,
    @ColumnInfo(name = "fragment") val fragment: String,
    @ColumnInfo(name = "match_info") val matchInfo: ByteArray,
)
