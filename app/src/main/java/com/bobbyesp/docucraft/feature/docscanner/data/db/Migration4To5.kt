/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * From a table of scans to the catalogue of documents.
 *
 * Every document of version 4 is carried over with its row id and its uuid: navigation keys and the
 * viewer's memory of a session refer to documents by uuid. Nothing is deleted, whatever state a row
 * is in.
 *
 * Only the database is touched. The PDFs stay where they are, in `scans/pdf/`, and the catalogue
 * now says so with a path relative to the files directory instead of a `FileProvider` URI. Old
 * previews stay on disk too; the catalogue no longer keeps where they are.
 *
 * Room runs a migration inside a transaction, so a failure leaves version 4 as it was.
 */
internal object Migration4To5 : Migration(4, 5) {

    override fun migrate(db: SupportSQLiteDatabase) {
        SchemaVersion5.TABLES.forEach(db::execSQL)
        SchemaVersion5.INDICES.forEach(db::execSQL)

        copyDocuments(db)
        db.execSQL(CREATE_PAGES)

        // The index is built from the rows once they are all there, rather than row by row.
        SchemaVersion5.SEARCH_INDEXES.forEach(db::execSQL)
        SchemaVersion5.SEARCH_INDEX_SYNC.forEach(db::execSQL)
        db.execSQL("INSERT INTO `documents_fts`(`documents_fts`) VALUES ('rebuild')")

        SchemaVersion5.VIEWS.forEach(db::execSQL)

        // Last, so that they check the finished rows and not the copy while it is under way.
        DatabaseTriggers.ALL.forEach(db::execSQL)

        // The table's own triggers, which kept the old index in step, go with it.
        db.execSQL("DROP TABLE IF EXISTS `scanned_documents_fts`")
        db.execSQL("DROP TABLE `scanned_documents`")
    }

    /**
     * Copies every scan into `documents` as a managed document that came from the scanner, with its
     * row in `document_activity`.
     *
     * Row by row rather than with one `INSERT … SELECT`, because two things have to be decided for
     * each row with what is known about the others:
     * - **Its path.** See [LegacyDocumentPath].
     * - **Whether another row already has that path.** Version 4 named a file after its scan and
     *   overwrote a file of the same name, so two rows can point at one file, whose content is the
     *   later scan's. The latest row keeps the path. An earlier one keeps its row and its uuid with
     *   a path where there is nothing, marked as not found: the user sees it and decides, and the
     *   unique index on the path holds.
     */
    private fun copyDocuments(db: SupportSQLiteDatabase) {
        val pathsTaken = HashSet<String>()

        db.query(
                "SELECT `id`, `uuid`, `filename`, `title`, `description`, `path`, " +
                    "`createdTimestamp`, `fileSize`, `pageCount` " +
                    "FROM `scanned_documents` ORDER BY `id` DESC"
            )
            .use { scans ->
                while (scans.moveToNext()) {
                    val id = scans.getLong(0)
                    val uuid = scans.getString(1)
                    val filename = scans.getString(2)
                    val createdAt = scans.getLong(6)
                    val pageCount = scans.getInt(8)

                    val path = LegacyDocumentPath.relativePathOf(scans.getString(5), filename)
                    val hasItsFile = pathsTaken.add(path)

                    val document =
                        ContentValues().apply {
                            put("id", id)
                            put("uuid", uuid)
                            put("custody", "MANAGED")
                            put("origin", "SCAN")
                            put("original_name", filename)
                            put("title", scans.getStringOrNull(3))
                            put("description", scans.getStringOrNull(4))
                            put("mime_type", "application/pdf")
                            put("size_bytes", scans.getLong(7))
                            // A scanner that did not report its pages was recorded as 0 pages.
                            // That is "unknown": the pages are counted when the file is read.
                            if (pageCount > 0) put("page_count", pageCount)
                            put("is_encrypted", 0)
                            put("is_favorite", 0)
                            put("ocr_enabled", 0)
                            put(
                                "file_path",
                                if (hasItsFile) path else "$MISSING_DIRECTORY/$uuid.pdf",
                            )
                            put("captured_at", createdAt)
                            put("created_at", createdAt)
                            put("updated_at", createdAt)
                            put("content_updated_at", createdAt)
                        }
                    db.insert("documents", SQLiteDatabase.CONFLICT_ABORT, document)

                    val activity =
                        ContentValues().apply {
                            put("document_id", id)
                            // Never opened as far as the catalogue knows: its activity is when
                            // it was scanned.
                            put("last_activity_at", createdAt)
                            put("availability", if (hasItsFile) "UNKNOWN" else "NOT_FOUND")
                        }
                    db.insert("document_activity", SQLiteDatabase.CONFLICT_ABORT, activity)
                }
            }
    }

    private fun Cursor.getStringOrNull(column: Int): String? =
        if (isNull(column)) null else getString(column)

    /** Where a row is said to be when another row has its file: a folder nothing is saved in. */
    private const val MISSING_DIRECTORY = "missing"

    /**
     * One page for each page of each document, all still to be read. A document whose page count is
     * unknown gets none until its file is read.
     */
    private const val CREATE_PAGES =
        """
        WITH RECURSIVE seq(document_id, page_index, total) AS (
            SELECT id, 0, page_count FROM documents WHERE page_count > 0
            UNION ALL
            SELECT document_id, page_index + 1, total FROM seq WHERE page_index + 1 < total
        )
        INSERT INTO pages (document_id, page_index, text_status, attempts)
        SELECT document_id, page_index, 'PENDING', 0 FROM seq
        """
}
