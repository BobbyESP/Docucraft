/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bobbyesp.docucraft.feature.docscanner.data.db.DatabaseTriggers
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.db.long
import com.bobbyesp.docucraft.feature.docscanner.data.db.rows
import com.bobbyesp.docucraft.feature.docscanner.data.search.Fts4SearchIndex
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentLocations
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentOrigin
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScan
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ReadingPosition
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LinkedDocumentFacts
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.NewLinkedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.StoredDocument
import com.bobbyesp.scanner.ContentRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The documents of other apps against a real database: one document for a location, a limit that is
 * kept in the same transaction that goes over it, and statements that cannot reach a document the
 * app keeps.
 */
@RunWith(AndroidJUnit4::class)
class LinkedDocumentsRepositoryImplTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database =
        Room.inMemoryDatabaseBuilder(context, DocumentsDatabase::class.java)
            .addCallback(DatabaseTriggers.CreateOnNewDatabase)
            .build()
    private val locations = DocumentLocations(context)

    /** The clock of every repository here, moved by hand. */
    private var now = 1_000L
    private var uuids = 0

    private val linked =
        LinkedDocumentsRepositoryImpl(
            documentDao = database.documentDao(),
            now = { now },
            newUuid = { "linked-${++uuids}" },
        )
    private val documents =
        DocumentsRepositoryImpl(database.documentDao(), locations, now = { now })
    private val activity =
        DocumentActivityRepositoryImpl(database.activityDao(), locations, now = { now })

    private val db
        get() = database.openHelper.writableDatabase

    @After
    fun closeDatabase() {
        database.close()
    }

    // --- registering ---

    // It has activity, like any document, and no pages: its text is not read.
    @Test
    fun aLinkedDocumentIsARowWithItsActivityAndNoPages() = runBlocking {
        val registration = link("content://other.app/1", "Contract", at = 2_000)

        assertEquals("linked-1", registration.uuid)
        assertEquals(
            listOf("linked-1|LINKED|null|Contract|content://other.app/1|null|0|null|2000"),
            db.rows(
                "SELECT uuid, custody, origin, original_name, uri, file_path, " +
                    "has_persisted_permission, page_count, created_at FROM documents"
            ),
        )
        assertEquals(
            listOf("null|2000|UNKNOWN"),
            db.rows("SELECT last_opened_at, last_activity_at, availability FROM document_activity"),
        )
        assertEquals(0L, db.long("SELECT COUNT(*) FROM pages"))
    }

    @Test
    fun itIsReadBackAsADocumentOfAnotherApp() = runBlocking {
        link("content://other.app/1", "Contract", at = 2_000, held = true)

        val document = documents.getDocument("linked-1") as Document.Linked

        assertEquals(ContentRef("content://other.app/1"), document.location)
        assertEquals("Contract", document.name)
        assertTrue(document.hasPersistedPermission)
    }

    // Not in the library: it is in no folder, has no tags and is not searched.
    @Test
    fun itIsNotInTheLibrary() = runBlocking {
        link("content://other.app/1", "Contract", at = 2_000)

        assertTrue(documents.observeDocuments().first().isEmpty())
    }

    // The location is what identifies it. Opening it again must not fill Recents with copies.
    @Test
    fun theSameLocationIsTheSameDocument() = runBlocking {
        val first = link("content://other.app/1", "Contract", at = 2_000)
        val again = link("content://other.app/1", "Contract (renamed)", at = 9_000)

        assertEquals(first.uuid, again.uuid)
        assertEquals(listOf("linked-1|Contract|2000"), documentRows())
    }

    // The other app can lend it on other terms the next time.
    @Test
    fun whetherThePermissionIsKeptIsNotedAgainEachTime() = runBlocking {
        link("content://other.app/1", "Contract", at = 2_000, held = false)

        link("content://other.app/1", "Contract", at = 3_000, held = true)

        assertEquals(listOf("1"), db.rows("SELECT has_persisted_permission FROM documents"))
    }

    // --- the limit ---

    @Test
    fun goingOverTheLimitForgetsTheOnesUsedLongestAgo() = runBlocking {
        link("content://other.app/1", "first", at = 1_000)
        link("content://other.app/2", "second", at = 2_000)
        link("content://other.app/3", "third", at = 3_000)
        // The oldest was opened since: it is now the one used last.
        now = 4_000
        activity.recordOpened("linked-1")

        val registration = link("content://other.app/4", "fourth", at = 5_000, limit = 3)

        assertEquals(listOf(ContentRef("content://other.app/2")), registration.forgotten)
        assertEquals(
            listOf("first", "third", "fourth"),
            db.rows("SELECT original_name FROM documents ORDER BY id"),
        )
        // With the document goes what was noted about it.
        assertEquals(3L, db.long("SELECT COUNT(*) FROM document_activity"))
    }

    @Test
    fun withinTheLimitNothingIsForgotten() = runBlocking {
        link("content://other.app/1", "first", at = 1_000)

        val registration = link("content://other.app/2", "second", at = 2_000, limit = 2)

        assertTrue(registration.forgotten.isEmpty())
        assertEquals(2L, db.long("SELECT COUNT(*) FROM documents"))
    }

    // Opening one that is already there adds nothing, so it must not cost another its place.
    @Test
    fun registeringAKnownLocationAtTheLimitForgetsNothing() = runBlocking {
        link("content://other.app/1", "first", at = 1_000)
        link("content://other.app/2", "second", at = 2_000)

        val registration = link("content://other.app/1", "first", at = 3_000, limit = 2)

        assertTrue(registration.forgotten.isEmpty())
        assertEquals(2L, db.long("SELECT COUNT(*) FROM documents"))
    }

    // The limit is on references to other apps' files. Nothing automatic removes a document the
    // app keeps, however many there are and however long ago they were used.
    @Test
    fun theLimitNeverCostsADocumentTheAppKeeps() = runBlocking {
        repeat(4) { save("scan-$it", at = 100L + it) }
        link("content://other.app/1", "first", at = 1_000)

        link("content://other.app/2", "second", at = 2_000, limit = 1)

        assertEquals(4, documents.observeDocuments().first().size)
        assertEquals(
            listOf("second"),
            db.rows("SELECT original_name FROM documents WHERE custody = 'LINKED'"),
        )
    }

    // --- forgetting ---

    @Test
    fun forgettingALinkedDocumentRemovesTheReferenceAndSaysWhereItWas() = runBlocking {
        link("content://other.app/1", "Contract", at = 2_000)

        assertEquals(ContentRef("content://other.app/1"), linked.forget("linked-1"))

        assertEquals(0L, db.long("SELECT COUNT(*) FROM documents"))
        assertEquals(0L, db.long("SELECT COUNT(*) FROM document_activity"))
    }

    // Removing from Recents is offered for other apps' documents. It must never be a way to lose
    // one of the app's own.
    @Test
    fun aDocumentTheAppKeepsCannotBeForgottenThroughHere() = runBlocking {
        save("scan", at = 1_000)

        assertNull(linked.forget("uuid-scan"))
        assertNull(linked.forget("no-such-document"))

        assertEquals(1, documents.observeDocuments().first().size)
    }

    // --- describing ---

    @Test
    fun whatALinkedDocumentTurnedOutToBeIsNoted() = runBlocking {
        link("content://other.app/1", "Contract", at = 2_000)

        now = 3_000
        linked.describe("linked-1", LinkedDocumentFacts(4_096, "abc", 12, isProtected = false))

        assertEquals(listOf("4096|abc|12|0|3000|3000"), factRows())
    }

    // A document that could not be opened this time is not one whose pages are unknown again.
    @Test
    fun whatCouldNotBeLearntThisTimeIsLeftAsItWas() = runBlocking {
        link("content://other.app/1", "Contract", at = 2_000)
        now = 3_000
        linked.describe("linked-1", LinkedDocumentFacts(4_096, "abc", 12, isProtected = false))

        now = 4_000
        linked.describe("linked-1", LinkedDocumentFacts(null, null, null, isProtected = true))

        assertEquals(listOf("4096|abc|12|1|4000|3000"), factRows())
    }

    // Asked on every opening of a file that almost never changes: a write here is heard by every
    // list of the library.
    @Test
    fun describingItTheSameWayAgainWritesNothing() = runBlocking {
        link("content://other.app/1", "Contract", at = 2_000)
        now = 3_000
        linked.describe("linked-1", LinkedDocumentFacts(4_096, "abc", 12, isProtected = false))

        now = 8_000
        linked.describe("linked-1", LinkedDocumentFacts(4_096, "abc", 12, isProtected = false))

        assertEquals(listOf("4096|abc|12|0|3000|3000"), factRows())
    }

    // Another app's file can be replaced. Its content is then dated again.
    @Test
    fun aFileWithOtherContentIsDatedAgain() = runBlocking {
        link("content://other.app/1", "Contract", at = 2_000)
        now = 3_000
        linked.describe("linked-1", LinkedDocumentFacts(4_096, "abc", 12, isProtected = false))

        now = 5_000
        linked.describe("linked-1", LinkedDocumentFacts(1_024, "def", 3, isProtected = false))

        assertEquals(listOf("1024|def|3|0|5000|5000"), factRows())
    }

    @Test
    fun aDocumentTheAppKeepsIsNotDescribedThroughHere() = runBlocking {
        save("scan", at = 1_000)

        now = 6_000
        linked.describe("uuid-scan", LinkedDocumentFacts(7, "other", 99, isProtected = true))

        assertEquals(listOf("1|hash-scan|1|0|1000|1000"), factRows())
    }

    // --- keeping it in the library ---

    // It is the same document: what was noted about it is about the one the app now keeps.
    @Test
    fun aSavedDocumentIsTheSameRowWithAnotherCustody() = runBlocking {
        link("content://other.app/1", "Contract", at = 2_000, held = true)
        now = 3_000
        activity.recordOpened("linked-1")
        activity.rememberReadingPosition("linked-1", ReadingPosition(4, 0.25f))

        now = 6_000
        assertTrue(
            linked.keepInLibrary(
                "linked-1",
                stored("linked-1", pageCount = 5),
                recognizeText = false,
            )
        )

        assertEquals(
            listOf(
                "linked-1|MANAGED|IMPORT|documents/linked-1.pdf|null|content://other.app/1|" +
                    "null|2048|hash-linked-1|5|2000|6000|6000"
            ),
            db.rows(
                "SELECT uuid, custody, origin, file_path, uri, source_uri, " +
                    "has_persisted_permission, size_bytes, content_hash, page_count, created_at, " +
                    "updated_at, content_updated_at FROM documents"
            ),
        )
        assertEquals(
            listOf("3000|3000|4|0.25"),
            db.rows(
                "SELECT last_opened_at, last_activity_at, reading_page, reading_offset " +
                    "FROM document_activity"
            ),
        )
    }

    // A document the app keeps has a page for each of its pages, waiting for its text to be read.
    @Test
    fun aSavedDocumentGetsItsPages() = runBlocking {
        link("content://other.app/1", "Contract", at = 2_000)

        linked.keepInLibrary("linked-1", stored("linked-1", pageCount = 3), recognizeText = false)

        assertEquals(
            listOf("0|PENDING", "1|PENDING", "2|PENDING"),
            db.rows("SELECT page_index, text_status FROM pages ORDER BY page_index"),
        )
    }

    @Test
    fun aSavedDocumentIsInTheLibraryAndIsFoundByItsName() = runBlocking {
        link("content://other.app/1", "Contrato de alquiler", at = 2_000)

        linked.keepInLibrary("linked-1", stored("linked-1", pageCount = 1), recognizeText = false)

        val saved = documents.observeDocuments().first().single()
        assertEquals("linked-1", saved.uuid)
        assertEquals(DocumentOrigin.IMPORT, saved.origin)
        assertEquals(
            listOf("linked-1"),
            Fts4SearchIndex(database.searchDao()).search("alquiler").map { it.documentUuid },
        )
    }

    // The reference is gone with the save. The other app's file is still there, and opening it
    // again is opening another app's file again.
    @Test
    fun theSameLocationOpenedAfterSavingIsANewLinkedDocument() = runBlocking {
        link("content://other.app/1", "Contract", at = 2_000)
        linked.keepInLibrary("linked-1", stored("linked-1", pageCount = 1), recognizeText = false)

        val again = link("content://other.app/1", "Contract", at = 9_000)

        assertEquals("linked-2", again.uuid)
        assertEquals(
            listOf("linked-1|MANAGED", "linked-2|LINKED"),
            db.rows("SELECT uuid, custody FROM documents ORDER BY id"),
        )
    }

    @Test
    fun onlyALinkedDocumentCanBeKeptThroughHere() = runBlocking {
        save("scan", at = 1_000)

        assertFalse(
            linked.keepInLibrary("uuid-scan", stored("other", pageCount = 9), recognizeText = false)
        )
        assertFalse(
            linked.keepInLibrary(
                "no-such-document",
                stored("other", pageCount = 9),
                recognizeText = false,
            )
        )

        assertEquals(
            listOf("documents/uuid-scan.pdf|1"),
            db.rows("SELECT file_path, page_count FROM documents"),
        )
        assertEquals(1L, db.long("SELECT COUNT(*) FROM pages"))
    }

    // --- already in the library ---

    @Test
    fun aDocumentOfTheLibraryIsFoundByItsContent() = runBlocking {
        save("first", at = 1_000)
        save("second", at = 2_000)

        assertEquals("uuid-second", documents.findInLibrary("hash-second")?.uuid)
        assertNull(documents.findInLibrary("hash-of-nothing"))
    }

    // Importing a duplicate is allowed, so there can be several. The one found is the first.
    @Test
    fun ofSeveralWithTheSameContentTheOldestIsFound() = runBlocking {
        save("first", at = 1_000)
        save("second", at = 2_000)
        db.execSQL("UPDATE documents SET content_hash = 'same'")

        assertEquals("uuid-first", documents.findInLibrary("same")?.uuid)
    }

    // What is in the bin is not in the library, and a reference to another app's file is not a
    // copy of it: neither makes saving a duplicate.
    @Test
    fun theBinAndOtherAppsDocumentsDoNotCount() = runBlocking {
        save("binned", at = 1_000)
        db.execSQL("UPDATE documents SET trashed_at = 5000")
        link("content://other.app/1", "Contract", at = 2_000)
        linked.describe("linked-1", LinkedDocumentFacts(1, "hash-binned", 1, isProtected = false))

        assertNull(documents.findInLibrary("hash-binned"))
    }

    private fun stored(name: String, pageCount: Int?) =
        StoredDocument(
            filePath = "documents/$name.pdf",
            sizeBytes = 2_048,
            contentHash = "hash-$name",
            pageCount = pageCount,
        )

    private fun documentRows(): List<String> =
        db.rows("SELECT uuid, original_name, created_at FROM documents ORDER BY id")

    private fun factRows(): List<String> =
        db.rows(
            "SELECT size_bytes, content_hash, page_count, is_encrypted, updated_at, " +
                "content_updated_at FROM documents"
        )

    private suspend fun link(
        uri: String,
        name: String,
        at: Long,
        held: Boolean = false,
        limit: Int = 50,
    ) = run {
        now = at
        linked.register(NewLinkedDocument(ContentRef(uri), name, held), limit)
    }

    private suspend fun save(name: String, at: Long) {
        now = at
        documents.addScan(
            NewScan(
                uuid = "uuid-$name",
                originalName = name,
                filePath = "documents/uuid-$name.pdf",
                sizeBytes = 1,
                contentHash = "hash-$name",
                pageCount = 1,
                capturedAtEpochMillis = at,
            )
        )
    }
}
