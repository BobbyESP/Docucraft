/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The rules of the catalogue that the tables cannot state on their own. Room has no way to declare
 * a `CHECK`, so they are triggers. The domain checks the same rules first, with a message for the
 * user; these are what is left if a write ever gets past it.
 *
 * There is one list, [ALL], used both when a database is created ([CreateOnNewDatabase]) and by the
 * migration that introduces them. Two lists would let a new database and a migrated one differ.
 *
 * They are written for the oldest SQLite the app runs on (3.9, API 24): no `WITH` inside a trigger,
 * which is why a folder moved into one of its own descendants is caught by the use case, not here.
 */
object DatabaseTriggers {

    /**
     * What a row of `documents` must look like for its custody.
     *
     * `COALESCE` matters: a comparison with `NULL` is `NULL`, not false, and `WHEN NOT (NULL)` does
     * not fire. Without it a managed document with no origin would pass.
     *
     * A managed document may have no page count yet: an old scan can have been catalogued without
     * one, and it is filled in when its file is first read. It may not have zero pages.
     */
    private const val CUSTODY_IS_CONSISTENT =
        """COALESCE(
            (NEW.custody = 'MANAGED' AND NEW.file_path IS NOT NULL AND NEW.uri IS NULL
                AND NEW.origin IN ('SCAN', 'IMPORT')
                AND (NEW.page_count IS NULL OR NEW.page_count >= 1))
            OR
            (NEW.custody = 'LINKED' AND NEW.uri IS NOT NULL AND NEW.file_path IS NULL
                AND NEW.origin IS NULL AND NEW.folder_id IS NULL
                AND NEW.trashed_at IS NULL AND NEW.is_favorite = 0
                AND NEW.ocr_enabled = 0),
            0)"""

    private const val CUSTODY_MESSAGE = "documents: columns do not match custody"

    /**
     * A folder is not its own parent, and two folders in the root do not share a name. The unique
     * index on (`parent_id`, `normalized_name`) covers every other folder, but not the root, where
     * `parent_id` is `NULL` and SQLite counts every `NULL` as different.
     *
     * `IS NOT`, to leave the row itself out: while a row is being inserted without an explicit id,
     * `NEW.id` is not a usable number yet, and `<>` against it would match nothing.
     */
    private const val FOLDER_RULES =
        """SELECT RAISE(ABORT, 'folders: a folder cannot be its own parent')
            WHERE NEW.parent_id = NEW.id;
        SELECT RAISE(ABORT, 'folders: name already used in the root')
            WHERE NEW.parent_id IS NULL AND EXISTS (
                SELECT 1 FROM folders
                WHERE parent_id IS NULL
                    AND normalized_name = NEW.normalized_name
                    AND id IS NOT NEW.id);"""

    val ALL: List<String> =
        listOf(
            """CREATE TRIGGER documents_custody_insert BEFORE INSERT ON documents
        WHEN NOT $CUSTODY_IS_CONSISTENT
        BEGIN
            SELECT RAISE(ABORT, '$CUSTODY_MESSAGE');
        END""",
            """CREATE TRIGGER documents_custody_update BEFORE UPDATE ON documents
        WHEN NOT $CUSTODY_IS_CONSISTENT
        BEGIN
            SELECT RAISE(ABORT, '$CUSTODY_MESSAGE');
        END""",
            // Another app's document is not organized: it only appears in Recents.
            """CREATE TRIGGER document_tags_managed_only BEFORE INSERT ON document_tags
        WHEN (SELECT custody FROM documents WHERE id = NEW.document_id) = 'LINKED'
        BEGIN
            SELECT RAISE(ABORT, 'document_tags: only managed documents can be tagged');
        END""",
            """CREATE TRIGGER folders_rules_insert BEFORE INSERT ON folders
        BEGIN
            $FOLDER_RULES
        END""",
            """CREATE TRIGGER folders_rules_update
        BEFORE UPDATE OF parent_id, normalized_name ON folders
        BEGIN
            $FOLDER_RULES
        END""",
        )

    /** Creates the triggers in a database that is being created, as opposed to migrated. */
    object CreateOnNewDatabase : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            ALL.forEach(db::execSQL)
        }
    }
}
