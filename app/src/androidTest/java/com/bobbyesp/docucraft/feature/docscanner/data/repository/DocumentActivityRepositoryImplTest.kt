/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bobbyesp.docucraft.feature.docscanner.data.db.DatabaseTriggers
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.db.rows
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentLocations
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentAvailability
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScan
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ReadingPosition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What is noted about documents as they are used, against a real database: Recents is an order over
 * two tables, and the last activity is worked out by the statement that writes it.
 */
@RunWith(AndroidJUnit4::class)
class DocumentActivityRepositoryImplTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database =
        Room.inMemoryDatabaseBuilder(context, DocumentsDatabase::class.java)
            .addCallback(DatabaseTriggers.CreateOnNewDatabase)
            .build()
    private val locations = DocumentLocations(context)

    /** The clock of both repositories, moved by hand. */
    private var now = 1_000L

    private val documents =
        DocumentsRepositoryImpl(database.documentDao(), locations, now = { now })
    private val activity =
        DocumentActivityRepositoryImpl(database.activityDao(), locations, now = { now })

    @After
    fun closeDatabase() {
        database.close()
    }

    // --- Recents ---

    // A document that was saved and never opened is recent because it is new.
    @Test
    fun aDocumentThatWasNeverOpenedCountsFromWhenItWasSaved() = runBlocking {
        save("first", at = 1_000)
        save("second", at = 2_000)

        assertEquals(listOf("second", "first"), recents())
        assertEquals(
            listOf(null, null),
            activity.observeRecents(10).first().map {
                it.lastOpenedAtEpochMillis
            },
        )
    }

    @Test
    fun openingADocumentBringsItToTheFront() = runBlocking {
        save("first", at = 1_000)
        save("second", at = 2_000)
        save("third", at = 3_000)

        open("first", at = 4_000)

        assertEquals(listOf("first", "third", "second"), recents())
        assertEquals(4_000L, activity.observeRecents(10).first().first().lastOpenedAtEpochMillis)
    }

    @Test
    fun recentsHoldsNoMoreThanItIsAskedFor() = runBlocking {
        repeat(5) { save("document-$it", at = 1_000L + it) }

        assertEquals(listOf("document-4", "document-3"), recents(limit = 2))
    }

    // Two documents saved in the same millisecond must not swap places between two readings.
    @Test
    fun documentsOfTheSameInstantKeepOneOrder() = runBlocking {
        save("first", at = 1_000)
        save("second", at = 1_000)

        assertEquals(listOf("second", "first"), recents())
    }

    @Test
    fun aDocumentInTheBinIsNotRecent() = runBlocking {
        save("kept", at = 1_000)
        save("binned", at = 2_000)
        open("binned", at = 3_000)

        database.openHelper.writableDatabase.execSQL(
            "UPDATE documents SET trashed_at = 5000 WHERE uuid = 'uuid-binned'"
        )

        assertEquals(listOf("kept"), recents())
    }

    // Another app's document is in no list of the library. Recents is where it is found.
    @Test
    fun aDocumentOfAnotherAppIsRecentToo() = runBlocking {
        save("own", at = 1_000)
        link("content://other.app/shared.pdf", name = "shared", at = 2_000)

        assertEquals(listOf("shared", "own"), recents())
    }

    // --- opening ---

    @Test
    fun openingADocumentNotesWhenAndMakesItTheLastActivity() = runBlocking {
        save("document", at = 1_000)

        open("document", at = 7_000)

        assertEquals(listOf("7000|7000"), activityRows("last_opened_at, last_activity_at"))
    }

    // The last activity is the later of the two. A clock set back between saving and opening must
    // not send a document behind the day it was saved.
    @Test
    fun theLastActivityIsNeverBeforeTheDocumentWasSaved() = runBlocking {
        save("document", at = 5_000)

        open("document", at = 3_000)

        assertEquals(listOf("3000|5000"), activityRows("last_opened_at, last_activity_at"))
    }

    @Test
    fun openingADocumentTheCatalogueDoesNotHaveChangesNothing() = runBlocking {
        save("document", at = 1_000)

        now = 9_000
        activity.recordOpened("no-such-document")

        assertEquals(listOf("null|1000"), activityRows("last_opened_at, last_activity_at"))
    }

    // --- availability ---

    @Test
    fun whetherAFileCouldBeReachedIsNotedWithWhenItWasLookedFor() = runBlocking {
        save("document", at = 1_000)

        now = 6_000
        activity.recordAvailability("uuid-document", DocumentAvailability.NOT_FOUND)

        assertEquals(
            listOf("NOT_FOUND|6000"),
            activityRows("availability, availability_checked_at"),
        )
        assertEquals(
            DocumentAvailability.NOT_FOUND,
            activity.observeRecents(10).first().single().availability,
        )
    }

    // Looking for a file is not using the document: it must not move it in Recents.
    @Test
    fun notingAvailabilityDoesNotCountAsActivity() = runBlocking {
        save("first", at = 1_000)
        save("second", at = 2_000)

        now = 8_000
        activity.recordAvailability("uuid-first", DocumentAvailability.AVAILABLE)

        assertEquals(listOf("second", "first"), recents())
    }

    // --- reading position ---

    @Test
    fun aDocumentIsReadWhereItWasLeft() = runBlocking {
        save("document", at = 1_000, pageCount = 10)

        assertNull(activity.readingPosition("uuid-document"))

        activity.rememberReadingPosition("uuid-document", ReadingPosition(4, 0.25f))

        assertEquals(ReadingPosition(4, 0.25f), activity.readingPosition("uuid-document"))
        assertEquals(listOf("4|0.25"), activityRows("reading_page, reading_offset"))
    }

    // A document can be replaced by a shorter one. The reader was near the end, and is taken to
    // the last page it has now rather than to its start.
    @Test
    fun aPositionOnAPageTheDocumentNoLongerHasIsReadAsItsLastPage() = runBlocking {
        save("document", at = 1_000, pageCount = 10)
        activity.rememberReadingPosition("uuid-document", ReadingPosition(8, 0.5f))

        database.openHelper.writableDatabase.execSQL("UPDATE documents SET page_count = 3")

        assertEquals(ReadingPosition(2, 0f), activity.readingPosition("uuid-document"))
    }

    @Test
    fun aPositionIsKeptForADocumentWhosePagesWereNeverCounted() = runBlocking {
        save("document", at = 1_000)
        database.openHelper.writableDatabase.execSQL("UPDATE documents SET page_count = NULL")

        activity.rememberReadingPosition("uuid-document", ReadingPosition(30, 0.5f))

        assertEquals(ReadingPosition(30, 0.5f), activity.readingPosition("uuid-document"))
    }

    // Reading is not opening: where the reader is changes all the time, and Recents does not.
    @Test
    fun keepingAPositionDoesNotCountAsActivity() = runBlocking {
        save("first", at = 1_000)
        save("second", at = 2_000)

        now = 9_000
        activity.rememberReadingPosition("uuid-first", ReadingPosition(0, 0.5f))

        assertEquals(listOf("second", "first"), recents())
    }

    @Test
    fun forgettingPositionsForgetsEveryOneAndNothingElse() = runBlocking {
        save("first", at = 1_000)
        save("second", at = 2_000)
        open("first", at = 3_000)
        activity.rememberReadingPosition("uuid-first", ReadingPosition(0, 0.5f))
        activity.rememberReadingPosition("uuid-second", ReadingPosition(0, 0.9f))

        activity.forgetReadingPositions()

        assertNull(activity.readingPosition("uuid-first"))
        assertNull(activity.readingPosition("uuid-second"))
        assertEquals(
            listOf("3000|3000|null|null", "null|2000|null|null"),
            activityRows("last_opened_at, last_activity_at, reading_page, reading_offset"),
        )
    }

    @Test
    fun aPositionForADocumentTheCatalogueDoesNotHaveIsNeitherKeptNorRead() = runBlocking {
        save("document", at = 1_000)

        activity.rememberReadingPosition("no-such-document", ReadingPosition(1, 0.5f))

        assertNull(activity.readingPosition("no-such-document"))
        assertEquals(listOf("null|null"), activityRows("reading_page, reading_offset"))
    }

    private suspend fun recents(limit: Int = 10): List<String> =
        activity.observeRecents(limit).first().map { it.document.originalName }

    private fun activityRows(columns: String): List<String> =
        database.openHelper.writableDatabase.rows("SELECT $columns FROM document_activity")

    private suspend fun save(name: String, at: Long, pageCount: Int = 1) {
        now = at
        documents.addScan(
            NewScan(
                uuid = "uuid-$name",
                originalName = name,
                filePath = "documents/uuid-$name.pdf",
                sizeBytes = 1,
                contentHash = "hash-$name",
                pageCount = pageCount,
                capturedAtEpochMillis = at,
            )
        )
    }

    private suspend fun open(name: String, at: Long) {
        now = at
        activity.recordOpened("uuid-$name")
    }

    /** A linked document, written as rows: nothing registers one yet. */
    private fun link(uri: String, name: String, at: Long) {
        val db = database.openHelper.writableDatabase
        db.execSQL(
            """INSERT INTO documents (uuid, custody, original_name, mime_type, is_encrypted,
                is_favorite, ocr_enabled, uri, has_persisted_permission, created_at, updated_at,
                content_updated_at)
            VALUES ('uuid-$name', 'LINKED', '$name', 'application/pdf', 0, 0, 0, '$uri', 0, $at,
                $at, $at)"""
        )
        db.execSQL(
            """INSERT INTO document_activity (document_id, last_activity_at, availability)
            SELECT id, $at, 'UNKNOWN' FROM documents WHERE uuid = 'uuid-$name'"""
        )
    }
}
