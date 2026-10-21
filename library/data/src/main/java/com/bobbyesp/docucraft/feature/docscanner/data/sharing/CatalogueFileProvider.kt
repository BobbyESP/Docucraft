/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.sharing

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.sqlite.db.SimpleSQLiteQuery
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.db.LegacyDocumentPath
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentStorageImpl
import org.koin.core.context.GlobalContext

/**
 * The provider the app hands its documents to other apps through, and to its own viewer.
 *
 * A plain `FileProvider` names a file by its name on disk. Documents are stored under their uuid,
 * so an app receiving a shared document would show the user `3f2c8a1e-….pdf`. This one answers with
 * the name the document has in the catalogue, the same one the user sees in the app.
 *
 * Everything else, including which folders are served and to whom, is `FileProvider`'s.
 */
class CatalogueFileProvider : FileProvider() {

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = super.query(uri, projection, selection, selectionArgs, sortOrder)

        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameColumn < 0) return cursor
        val name = catalogueNameOf(uri) ?: return cursor

        return cursor.use { it.withValue(column = nameColumn, value = name) }
    }

    /**
     * What the catalogue calls the document served at [uri], as a file name. `null` when the
     * catalogue has no document there, or cannot be asked: the file's own name will do.
     */
    private fun catalogueNameOf(uri: Uri): String? =
        try {
            val filePath = filePathOf(uri) ?: return null
            // Asked of SQLite directly, not through Room: another app's query arrives on a binder
            // thread, but the app's own can arrive on the main thread, where Room refuses to run.
            // It is one row by a unique index.
            GlobalContext.getOrNull()
                ?.get<DocumentsDatabase>()
                ?.openHelper
                ?.readableDatabase
                ?.query(SimpleSQLiteQuery(NAME_OF_DOCUMENT_AT, arrayOf<Any?>(filePath)))
                ?.use { row -> if (row.moveToFirst()) row.getString(0) else null }
                ?.toFileName()
        } catch (e: Exception) {
            Log.w(TAG, "Could not ask the catalogue for the name of $uri", e)
            null
        }

    /** The path the catalogue keeps for the file served at [uri]: the inverse of the provider. */
    private fun filePathOf(uri: Uri): String? {
        val segments = uri.pathSegments
        return if (segments.firstOrNull() == DocumentStorageImpl.DIRECTORY) {
            segments.joinToString("/")
        } else {
            LegacyDocumentPath.relativePathOf(uri.toString())
        }
    }

    /** A name that is safe as a file's: no separators, and the extension a PDF is opened by. */
    private fun String.toFileName(): String? {
        val safe = replace(UNSAFE_IN_A_FILE_NAME, "_").trim()
        if (safe.isEmpty()) return null
        return if (safe.endsWith(EXTENSION, ignoreCase = true)) safe else safe + EXTENSION
    }

    /** A copy of this single-row cursor with one column's value replaced. */
    private fun Cursor.withValue(column: Int, value: String): Cursor {
        val copy = MatrixCursor(columnNames, 1)
        if (moveToFirst()) {
            copy.addRow(
                Array<Any?>(columnCount) { index ->
                    when {
                        index == column -> value
                        getType(index) == Cursor.FIELD_TYPE_INTEGER -> getLong(index)
                        getType(index) == Cursor.FIELD_TYPE_NULL -> null
                        else -> getString(index)
                    }
                }
            )
        }
        return copy
    }

    private companion object {
        const val TAG = "CatalogueFileProvider"
        const val EXTENSION = ".pdf"

        val UNSAFE_IN_A_FILE_NAME = Regex("""[/\\\p{Cntrl}]""")

        /** What a document is called on screen, as `Document.name` works it out. */
        const val NAME_OF_DOCUMENT_AT =
            "SELECT COALESCE(title, suggested_title, original_name) FROM documents " +
                "WHERE file_path = ?"
    }
}
