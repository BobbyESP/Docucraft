/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.DocumentTextStatusView
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.PageEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.PageLayoutEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.PageTextEntity
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.pendingPages
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

/**
 * The layout of a recognized page as it is stored. Not a data class: it holds an array.
 *
 * @property formatVersion The version of the binary format of [data].
 */
class StoredLayoutRow(
    @ColumnInfo(name = "format_version") val formatVersion: Int,
    @ColumnInfo(name = "data") val data: ByteArray,
    @ColumnInfo(name = "confidence") val confidence: Float?,
    @ColumnInfo(name = "engine") val engine: String?,
    @ColumnInfo(name = "language") val language: String?,
)

/**
 * `pages` and the text that hangs from them. A page is found by its document's uuid and its index,
 * which is all that travels above the data layer.
 *
 * Each write is one transaction over one page: its text and its state change together.
 */
@Dao
abstract class PageDao {
    @Query(
        "SELECT d.uuid AS document_uuid, p.page_index, p.text_status, p.text_origin, p.attempts " +
            "FROM pages p JOIN documents d ON d.id = p.document_id " +
            "WHERE d.uuid = :documentUuid ORDER BY p.page_index"
    )
    abstract suspend fun pagesOf(documentUuid: String): List<PageRow>

    @Query(
        "SELECT t.text FROM page_texts t JOIN pages p ON p.id = t.page_id " +
            "JOIN documents d ON d.id = p.document_id " +
            "WHERE d.uuid = :documentUuid ORDER BY p.page_index"
    )
    abstract suspend fun textOf(documentUuid: String): List<String>

    /** No row, and so `null`, for a document without pages: the view groups the pages there are. */
    @Query(
        "SELECT s.* FROM document_text_status s JOIN documents d ON d.id = s.document_id " +
            "WHERE d.uuid = :documentUuid"
    )
    abstract fun observeTextStatus(documentUuid: String): Flow<DocumentTextStatusView?>

    @Query(
        "SELECT p.page_index FROM pages p JOIN documents d ON d.id = p.document_id " +
            "WHERE d.uuid = :documentUuid AND p.text_status = 'PENDING' ORDER BY p.page_index"
    )
    abstract suspend fun pendingPagesOf(documentUuid: String): List<Int>

    /**
     * The documents of the library that have a pending page, or whose pages were never counted. The
     * bin is left out: nothing in it is searched.
     */
    @Query(
        """SELECT d.uuid FROM library_documents d
        WHERE d.page_count IS NULL
          OR EXISTS (
            SELECT 1 FROM pages p WHERE p.document_id = d.id AND p.text_status = 'PENDING')
        ORDER BY d.created_at DESC, d.id DESC"""
    )
    abstract suspend fun documentsWithPagesToRead(): List<String>

    /**
     * Counts the pages of a document that came without a count, and gives it a row for each.
     *
     * @return Whether there was such a document.
     */
    @Transaction
    open suspend fun createPages(documentUuid: String, count: Int, at: Long): Boolean {
        val documentId = idOfUncounted(documentUuid) ?: return false
        setPageCount(documentId, count, at)
        insert(pendingPages(documentId = documentId, count = count))
        return true
    }

    /**
     * The text first removed and then added, never replaced: replacing a row does not run the
     * triggers that keep the full-text index in step, and would leave the old words findable.
     *
     * @return Whether the page was there.
     */
    @Transaction
    open suspend fun storeText(
        documentUuid: String,
        pageIndex: Int,
        text: String,
        origin: ContentOrigin,
        confidence: Float?,
        engine: String?,
        language: String?,
        layout: PageLayoutEntity?,
        extractorVersion: Int,
        at: Long,
    ): Boolean {
        val pageId = pageId(documentUuid, pageIndex) ?: return false
        deleteText(pageId)
        deleteLayout(pageId)
        insert(PageTextEntity(pageId = pageId, text = text))
        layout?.let {
            insert(PageLayoutEntity(pageId, formatVersion = it.formatVersion, data = it.data))
        }
        setRead(
            pageId,
            PageTextStatus.EXTRACTED,
            origin,
            confidence,
            engine,
            language,
            extractorVersion,
            at,
        )
        return true
    }

    /** @return Whether the page was there. */
    @Transaction
    open suspend fun storeWithoutText(
        documentUuid: String,
        pageIndex: Int,
        status: PageTextStatus,
        extractorVersion: Int,
        at: Long,
    ): Boolean {
        val pageId = pageId(documentUuid, pageIndex) ?: return false
        deleteText(pageId)
        deleteLayout(pageId)
        setRead(pageId, status, null, null, null, null, extractorVersion, at)
        return true
    }

    /**
     * Where the words of a recognized page are, as they were written down, with how sure the
     * recognition was.
     */
    @Query(
        """SELECT l.format_version, l.data, p.confidence, p.engine, p.language
        FROM page_layouts l
          JOIN pages p ON p.id = l.page_id
          JOIN documents d ON d.id = p.document_id
        WHERE d.uuid = :documentUuid AND p.page_index = :pageIndex"""
    )
    abstract suspend fun layoutOf(documentUuid: String, pageIndex: Int): StoredLayoutRow?

    /**
     * Turns text recognition on or off for a document the app keeps, and puts its pages where that
     * leaves them, all at once.
     *
     * On: the pages that were waiting for it are to be read again. Off: what was recognized is
     * forgotten, text and layout, and those pages wait for it once more. The text a PDF has of its
     * own is never touched: it was not recognition that read it.
     *
     * @return Whether there was such a document.
     */
    @Transaction
    open suspend fun setTextRecognition(documentUuid: String, enabled: Boolean, at: Long): Boolean {
        val documentId = idOfManaged(documentUuid) ?: return false
        setOcrEnabled(documentId, enabled, at)
        if (enabled) {
            requeueWaitingForRecognition(documentId)
        } else {
            deleteRecognizedText(documentId)
            deleteRecognizedLayouts(documentId)
            forgetRecognition(documentId)
        }
        return true
    }

    @Query("SELECT id FROM documents WHERE uuid = :documentUuid AND custody = 'MANAGED'")
    protected abstract suspend fun idOfManaged(documentUuid: String): Long?

    @Query("UPDATE documents SET ocr_enabled = :enabled, updated_at = :at WHERE id = :documentId")
    protected abstract suspend fun setOcrEnabled(documentId: Long, enabled: Boolean, at: Long)

    @Query(
        "UPDATE pages SET text_status = 'PENDING', attempts = 0 " +
            "WHERE document_id = :documentId AND text_status = 'OCR_DISABLED'"
    )
    protected abstract suspend fun requeueWaitingForRecognition(documentId: Long)

    /** A plain delete, so that the full-text index forgets the words too. */
    @Query(
        "DELETE FROM page_texts WHERE page_id IN (" +
            "SELECT id FROM pages WHERE document_id = :documentId AND text_origin = 'RECOGNIZED')"
    )
    protected abstract suspend fun deleteRecognizedText(documentId: Long)

    @Query(
        "DELETE FROM page_layouts WHERE page_id IN (" +
            "SELECT id FROM pages WHERE document_id = :documentId)"
    )
    protected abstract suspend fun deleteRecognizedLayouts(documentId: Long)

    /**
     * Pages recognition read, and pages it found nothing on: neither is known any more. A page that
     * failed is left to be tried again, since it may be the document's own text that failed.
     */
    @Query(
        """UPDATE pages
        SET text_status = 'OCR_DISABLED', text_origin = NULL, confidence = NULL, engine = NULL,
            language = NULL, attempts = 0
        WHERE document_id = :documentId
          AND (text_origin = 'RECOGNIZED' OR text_status = 'NO_TEXT')"""
    )
    protected abstract suspend fun forgetRecognition(documentId: Long)

    /** @return How the page is left, or `null` when it is not there. */
    @Transaction
    open suspend fun recordFailure(
        documentUuid: String,
        pageIndex: Int,
        maxAttempts: Int,
    ): PageTextStatus? {
        val pageId = pageId(documentUuid, pageIndex) ?: return null
        addFailure(pageId, maxAttempts)
        return statusOf(pageId)
    }

    /**
     * A page left waiting for text recognition in a document that has it on is one that was read
     * just as it was being turned on, and is read again.
     *
     * A page read by an older extractor keeps its text until it is read again. One that was never
     * read has no version, and `NULL < :extractorVersion` leaves it alone.
     */
    @Query(
        """UPDATE pages SET text_status = 'PENDING', attempts = 0
        WHERE text_status = 'FAILED'
          OR (text_status <> 'PENDING' AND extractor_version < :extractorVersion)
          OR (text_status = 'OCR_DISABLED'
            AND document_id IN (SELECT id FROM documents WHERE ocr_enabled = 1))"""
    )
    abstract suspend fun requeue(extractorVersion: Int): Int

    @Query(
        "SELECT p.id FROM pages p JOIN documents d ON d.id = p.document_id " +
            "WHERE d.uuid = :documentUuid AND p.page_index = :pageIndex"
    )
    protected abstract suspend fun pageId(documentUuid: String, pageIndex: Int): Long?

    @Query(
        "SELECT id FROM documents " +
            "WHERE uuid = :documentUuid AND custody = 'MANAGED' AND page_count IS NULL"
    )
    protected abstract suspend fun idOfUncounted(documentUuid: String): Long?

    @Query("UPDATE documents SET page_count = :count, updated_at = :at WHERE id = :documentId")
    protected abstract suspend fun setPageCount(documentId: Long, count: Int, at: Long)

    @Query("DELETE FROM page_texts WHERE page_id = :pageId")
    protected abstract suspend fun deleteText(pageId: Long)

    @Query("DELETE FROM page_layouts WHERE page_id = :pageId")
    protected abstract suspend fun deleteLayout(pageId: Long)

    @Query(
        """UPDATE pages
        SET text_status = :status, text_origin = :origin, confidence = :confidence,
            engine = :engine, extractor_version = :extractorVersion, language = :language,
            attempts = 0, extracted_at = :at
        WHERE id = :pageId"""
    )
    protected abstract suspend fun setRead(
        pageId: Long,
        status: PageTextStatus,
        origin: ContentOrigin?,
        confidence: Float?,
        engine: String?,
        language: String?,
        extractorVersion: Int,
        at: Long,
    )

    @Query(
        """UPDATE pages
        SET attempts = attempts + 1,
            text_status = CASE WHEN attempts + 1 >= :maxAttempts THEN 'FAILED' ELSE 'PENDING' END
        WHERE id = :pageId"""
    )
    protected abstract suspend fun addFailure(pageId: Long, maxAttempts: Int)

    @Query("SELECT text_status FROM pages WHERE id = :pageId")
    protected abstract suspend fun statusOf(pageId: Long): PageTextStatus?

    @Insert protected abstract suspend fun insert(text: PageTextEntity)

    @Insert protected abstract suspend fun insert(layout: PageLayoutEntity)

    @Insert protected abstract suspend fun insert(pages: List<PageEntity>)
}
