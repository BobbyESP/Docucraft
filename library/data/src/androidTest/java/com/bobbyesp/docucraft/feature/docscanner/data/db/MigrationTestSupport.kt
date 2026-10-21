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

/**
 * A scan as version 4 stored it.
 *
 * @param path What version 4 kept as the document's location: its `FileProvider` URI. By default,
 *   the one of a file named after [filename].
 * @return The row id it was given.
 */
internal fun SupportSQLiteDatabase.insertVersion4Scan(
    uuid: String,
    filename: String,
    title: String? = null,
    description: String? = null,
    path: String = version4Location("$filename.pdf"),
    createdTimestamp: Long = 1_700_000_000_000,
    fileSize: Long = 2_048,
    pageCount: Int = 1,
): Long =
    insert(
        "scanned_documents",
        SQLiteDatabase.CONFLICT_ABORT,
        ContentValues().apply {
            put("uuid", uuid)
            put("filename", filename)
            put("title", title)
            put("description", description)
            put("path", path)
            put("createdTimestamp", createdTimestamp)
            put("fileSize", fileSize)
            put("pageCount", pageCount)
            put("thumbnail", "/data/user/0/com.bobbyesp.docucraft/files/previews/$filename.webp")
        },
    )

/** The URI version 4 stored for the file [encodedName], which is percent-encoded as it was. */
internal fun version4Location(encodedName: String): String =
    "content://com.bobbyesp.docucraft.fileprovider/scanned-pdfs/$encodedName"

/** Every row [sql] returns, its columns joined with `|` and a missing value written as `null`. */
internal fun SupportSQLiteDatabase.rows(sql: String): List<String> =
    query(sql).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    (0 until cursor.columnCount).joinToString("|") { column ->
                        if (cursor.isNull(column)) "null" else cursor.getString(column)
                    }
                )
            }
        }
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
