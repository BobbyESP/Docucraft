/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentTextStatusView
import com.bobbyesp.docucraft.feature.docscanner.domain.model.PageTextStatus
import com.bobbyesp.documentcontent.ContentOrigin
import kotlinx.coroutines.flow.Flow

/** What is read of a page, with its document's uuid in place of the row id pages hang from. */
data class PageRow(
    @ColumnInfo(name = "document_uuid") val documentUuid: String,
    @ColumnInfo(name = "page_index") val pageIndex: Int,
    @ColumnInfo(name = "text_status") val textStatus: PageTextStatus,
    @ColumnInfo(name = "text_origin") val textOrigin: ContentOrigin?,
    @ColumnInfo(name = "attempts") val attempts: Int,
)

@Dao
interface PageDao {

    @Query(
        "SELECT d.uuid AS document_uuid, p.page_index, p.text_status, p.text_origin, p.attempts " +
            "FROM pages p JOIN documents d ON d.id = p.document_id " +
            "WHERE d.uuid = :documentUuid ORDER BY p.page_index"
    )
    suspend fun pagesOf(documentUuid: String): List<PageRow>

    /** No row, and so `null`, for a document without pages: the view groups the pages there are. */
    @Query(
        "SELECT s.* FROM document_text_status s JOIN documents d ON d.id = s.document_id " +
            "WHERE d.uuid = :documentUuid"
    )
    fun observeTextStatus(documentUuid: String): Flow<DocumentTextStatusView?>
}
