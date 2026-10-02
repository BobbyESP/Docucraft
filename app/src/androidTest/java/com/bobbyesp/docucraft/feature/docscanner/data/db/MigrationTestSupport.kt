/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry

/** The database file the migration tests work on. Each test deletes it when it finishes. */
internal const val MigrationTestDatabase = "migration-test.db"

/** Builds each old version of the database from its exported schema, in `app/schemas`. */
internal fun migrationTestHelper(): MigrationTestHelper =
    MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DocumentsDatabase::class.java)

internal fun deleteMigrationTestDatabase() {
    InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(MigrationTestDatabase)
}

/**
 * A document as versions 1 to 3 stored it: the identifier was the primary key, as text.
 *
 * @param table `scanned_pdfs` in version 1, `scanned_documents` from version 2.
 */
internal fun SupportSQLiteDatabase.insertLegacyDocument(
    table: String,
    id: String,
    filename: String,
    title: String? = null,
    createdTimestamp: Long = 1_700_000_000_000,
    pageCount: Int = 1,
) {
    insert(
        table,
        SQLiteDatabase.CONFLICT_ABORT,
        ContentValues().apply {
            put("id", id)
            put("filename", filename)
            put("title", title)
            putNull("description")
            put("path", "content://com.bobbyesp.docucraft.fileprovider/scanned-pdfs/$filename.pdf")
            put("createdTimestamp", createdTimestamp)
            put("fileSize", 2_048L)
            put("pageCount", pageCount)
            putNull("thumbnail")
        },
    )
}

/** The first column of every row [sql] returns, as text. */
internal fun SupportSQLiteDatabase.strings(sql: String): List<String?> =
    query(sql).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

/** The first column of the first row [sql] returns, as a number. */
internal fun SupportSQLiteDatabase.long(sql: String): Long =
    query(sql).use { cursor ->
        check(cursor.moveToFirst()) { "No rows: $sql" }
        cursor.getLong(0)
    }
