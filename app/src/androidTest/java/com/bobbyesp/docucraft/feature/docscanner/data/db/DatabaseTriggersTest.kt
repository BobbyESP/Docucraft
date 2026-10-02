/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Each rule the triggers keep, with a write it lets through and one it stops. They run on the
 * device's own SQLite: the oldest the app supports is far behind the one on a desk.
 *
 * The rows are written as SQL, not through Room, because the point is what the database does with a
 * write the code above it should never have made.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseTriggersTest {

    private val database =
        Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                DocumentsDatabase::class.java,
            )
            .addCallback(DatabaseTriggers.CreateOnNewDatabase)
            .build()
    private val db: SupportSQLiteDatabase = database.openHelper.writableDatabase

    @After
    fun closeDatabase() {
        database.close()
    }

    // --- documents: columns match custody ---

    @Test
    fun aManagedDocumentWithItsFileItsOriginAndItsPagesIsAccepted() {
        db.insertDocument(managed("scan") + ("page_count" to 3))
        db.insertDocument(
            managed("import") + ("origin" to "IMPORT") + ("source_uri" to "content://a")
        )

        assertEquals(2, db.long("SELECT COUNT(*) FROM documents"))
    }

    // An old scan can have been catalogued without a page count; it is filled in later.
    @Test
    fun aManagedDocumentWhosePagesAreNotKnownYetIsAccepted() {
        db.insertDocument(managed("a") + ("page_count" to null))

        assertEquals(1, db.long("SELECT COUNT(*) FROM documents"))
    }

    // A comparison with NULL is NULL, not false. A condition that is not written for that lets
    // exactly these rows through.
    @Test
    fun aManagedDocumentWithoutAnOriginIsRejected() {
        assertRejected("documents: columns do not match custody") {
            db.insertDocument(managed("a") + ("origin" to null))
        }
    }

    @Test
    fun aManagedDocumentWithNoPagesOrNoFileOrAUriIsRejected() {
        assertRejected("documents: columns do not match custody") {
            db.insertDocument(managed("a") + ("page_count" to 0))
        }
        assertRejected("documents: columns do not match custody") {
            db.insertDocument(managed("b") + ("file_path" to null))
        }
        assertRejected("documents: columns do not match custody") {
            db.insertDocument(managed("c") + ("uri" to "content://other/c.pdf"))
        }
        assertRejected("documents: columns do not match custody") {
            db.insertDocument(managed("d") + ("origin" to "DOWNLOAD"))
        }
    }

    @Test
    fun aLinkedDocumentIsOnlyAReference() {
        db.insertDocument(linked("a"))
        // Its size and pages are known once it has been read, and unknown until then.
        db.insertDocument(linked("b") + ("page_count" to 7) + ("size_bytes" to 1024))

        assertEquals(2, db.long("SELECT COUNT(*) FROM documents"))
    }

    // Another app's document only appears in Recents: it is not organized, kept or recognized.
    @Test
    fun aLinkedDocumentWithAnythingOfTheLibraryIsRejected() {
        val folder = db.insertFolder("Invoices")
        val rejected =
            listOf<Pair<String, Any?>>(
                "folder_id" to folder,
                "is_favorite" to 1,
                "ocr_enabled" to 1,
                "trashed_at" to 1_700_000_000_000,
                "origin" to "IMPORT",
                "file_path" to "documents/x.pdf",
                "uri" to null,
            )
        for (column in rejected) {
            assertRejected("documents: columns do not match custody") {
                db.insertDocument(linked("a") + column)
            }
        }
    }

    @Test
    fun aCustodyThatDoesNotExistIsRejected() {
        assertRejected("documents: columns do not match custody") {
            db.insertDocument(managed("a") + ("custody" to "BORROWED"))
        }
    }

    @Test
    fun aDocumentCanBeChangedAsLongAsItStaysConsistent() {
        db.insertDocument(managed("a"))

        db.execSQL("UPDATE documents SET title = 'Renamed', is_favorite = 1 WHERE uuid = 'a'")

        assertEquals(listOf("Renamed|1"), db.rows("SELECT title, is_favorite FROM documents"))
    }

    @Test
    fun aChangeThatBreaksTheCustodyIsRejected() {
        db.insertDocument(managed("managed"))
        db.insertDocument(linked("linked"))

        assertRejected("documents: columns do not match custody") {
            db.execSQL("UPDATE documents SET origin = NULL WHERE uuid = 'managed'")
        }
        assertRejected("documents: columns do not match custody") {
            db.execSQL("UPDATE documents SET page_count = 0 WHERE uuid = 'managed'")
        }
        assertRejected("documents: columns do not match custody") {
            db.execSQL("UPDATE documents SET is_favorite = 1 WHERE uuid = 'linked'")
        }
    }

    // Saving a linked document into the library changes its custody and everything that hangs from
    // it at once. Done as several updates, the first of them would be a row that fits neither.
    @Test
    fun aLinkedDocumentBecomesManagedInASingleUpdate() {
        db.insertDocument(linked("a"))

        db.execSQL(
            "UPDATE documents SET custody = 'MANAGED', origin = 'IMPORT', " +
                "file_path = 'documents/a.pdf', source_uri = uri, uri = NULL, page_count = 2 " +
                "WHERE uuid = 'a'"
        )

        assertEquals(
            listOf("a|MANAGED|IMPORT|documents/a.pdf|null|content://other/a.pdf"),
            db.rows("SELECT uuid, custody, origin, file_path, uri, source_uri FROM documents"),
        )
        assertRejected("documents: columns do not match custody") {
            db.execSQL("UPDATE documents SET custody = 'LINKED' WHERE uuid = 'a'")
        }
    }

    // --- document_tags: only managed documents ---

    @Test
    fun aManagedDocumentCanBeTaggedAndALinkedOneCannot() {
        val managed = db.insertDocument(managed("managed"))
        val linked = db.insertDocument(linked("linked"))
        val tag = db.insertTag("Invoices")

        db.tag(managed, tag)
        assertEquals(1, db.long("SELECT COUNT(*) FROM document_tags"))

        assertRejected("document_tags: only managed documents can be tagged") {
            db.tag(linked, tag)
        }
    }

    // --- folders: names in the root, and no folder inside itself ---

    // The unique index cannot see this: in the root the parent is NULL, and SQLite counts every
    // NULL as different from every other.
    @Test
    fun twoFoldersInTheRootCannotShareAName() {
        db.insertFolder("Invoices")

        assertRejected("folders: name already used in the root") { db.insertFolder("Invoices") }
        db.insertFolder("Contracts")
        assertEquals(2, db.long("SELECT COUNT(*) FROM folders"))
    }

    @Test
    fun twoFoldersWithTheSameParentCannotShareANameAndWithDifferentParentsTheyCan() {
        val invoices = db.insertFolder("Invoices")
        val contracts = db.insertFolder("Contracts")
        db.insertFolder("2026", parent = invoices)
        db.insertFolder("2026", parent = contracts)

        // This one is the unique index's doing, not a trigger's.
        assertRejected("UNIQUE") { db.insertFolder("2026", parent = invoices) }
    }

    @Test
    fun aFolderCannotBeRenamedOrMovedOntoANameUsedInTheRoot() {
        db.insertFolder("Invoices")
        val contracts = db.insertFolder("Contracts")
        val nested = db.insertFolder("Invoices", parent = contracts)

        assertRejected("folders: name already used in the root") {
            db.execSQL(
                "UPDATE folders SET name = 'Invoices', normalized_name = 'invoices' WHERE id = $contracts"
            )
        }
        assertRejected("folders: name already used in the root") {
            db.execSQL("UPDATE folders SET parent_id = NULL WHERE id = $nested")
        }
    }

    // A rename compares the folder with the others, never with itself.
    @Test
    fun aFolderInTheRootCanBeChangedWithoutCollidingWithItself() {
        val invoices = db.insertFolder("Invoices")

        db.execSQL(
            "UPDATE folders SET name = 'INVOICES', normalized_name = 'invoices', color = 'terracotta' WHERE id = $invoices"
        )

        assertEquals(listOf("INVOICES|terracotta"), db.rows("SELECT name, color FROM folders"))
    }

    // Deeper cycles are the use case's to catch: a trigger cannot walk the ancestors on the oldest
    // SQLite the app runs on.
    @Test
    fun aFolderCannotBeItsOwnParent() {
        val invoices = db.insertFolder("Invoices")

        assertRejected("folders: a folder cannot be its own parent") {
            db.execSQL("UPDATE folders SET parent_id = $invoices WHERE id = $invoices")
        }
    }

    // --- what the rows look like ---

    private fun managed(uuid: String): Map<String, Any?> =
        mapOf(
            "uuid" to uuid,
            "custody" to "MANAGED",
            "origin" to "SCAN",
            "original_name" to "Scan_$uuid",
            "page_count" to 1,
            "file_path" to "documents/$uuid.pdf",
        )

    private fun linked(uuid: String): Map<String, Any?> =
        mapOf(
            "uuid" to uuid,
            "custody" to "LINKED",
            "original_name" to "$uuid.pdf",
            "uri" to "content://other/$uuid.pdf",
        )

    private fun SupportSQLiteDatabase.insertDocument(columns: Map<String, Any?>): Long =
        insert(
            "documents",
            SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("mime_type", "application/pdf")
                put("is_encrypted", 0)
                put("is_favorite", 0)
                put("ocr_enabled", 0)
                put("created_at", 1_700_000_000_000)
                put("updated_at", 1_700_000_000_000)
                put("content_updated_at", 1_700_000_000_000)
                for ((column, value) in columns) {
                    when (value) {
                        null -> putNull(column)
                        is Number -> put(column, value.toLong())
                        else -> put(column, value.toString())
                    }
                }
            },
        )

    private fun SupportSQLiteDatabase.insertFolder(name: String, parent: Long? = null): Long =
        insert(
            "folders",
            SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("uuid", "folder-${long("SELECT COUNT(*) FROM folders")}-$name")
                put("name", name)
                put("normalized_name", name.lowercase())
                put("parent_id", parent)
                put("created_at", 1_700_000_000_000)
                put("updated_at", 1_700_000_000_000)
            },
        )

    private fun SupportSQLiteDatabase.insertTag(name: String): Long =
        insert(
            "tags",
            SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("uuid", "tag-$name")
                put("name", name)
                put("normalized_name", name.lowercase())
                put("created_at", 1_700_000_000_000)
            },
        )

    private fun SupportSQLiteDatabase.tag(document: Long, tag: Long) {
        insert(
            "document_tags",
            SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("document_id", document)
                put("tag_id", tag)
                put("tagged_at", 1_700_000_000_000)
            },
        )
    }

    /** [write] is stopped by a constraint whose message contains [message]. */
    private fun assertRejected(message: String, write: () -> Unit) {
        try {
            write()
        } catch (e: SQLiteConstraintException) {
            assertTrue(
                "Rejected, but for another reason: ${e.message}",
                message in e.message.orEmpty(),
            )
            return
        }
        fail("The write was accepted; expected: $message")
    }
}
