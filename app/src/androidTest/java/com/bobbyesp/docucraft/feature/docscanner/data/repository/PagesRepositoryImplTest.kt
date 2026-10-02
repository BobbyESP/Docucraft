/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bobbyesp.docucraft.feature.docscanner.data.db.DatabaseTriggers
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentLocations
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScan
import com.bobbyesp.docucraft.feature.docscanner.domain.model.PageTextStatus
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentTextStatus
import com.bobbyesp.documentcontent.ContentOrigin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** What is read of a document's pages, through the view that counts them. */
@RunWith(AndroidJUnit4::class)
class PagesRepositoryImplTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database =
        Room.inMemoryDatabaseBuilder(context, DocumentsDatabase::class.java)
            .addCallback(DatabaseTriggers.CreateOnNewDatabase)
            .build()
    private val pages = PagesRepositoryImpl(database.pageDao())
    private val documents =
        DocumentsRepositoryImpl(database.documentDao(), DocumentLocations(context))
    private val db
        get() = database.openHelper.writableDatabase

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun aSavedDocumentHasAllItsPagesStillToBeRead() = runBlocking {
        document("a", pageCount = 3)

        assertEquals(listOf(0, 1, 2), pages.pagesOf("a").map { it.index })
        assertTrue(pages.pagesOf("a").all { it.textStatus == PageTextStatus.PENDING })
        assertEquals(
            DocumentTextStatus(pages = 3, pending = 3, failed = 0, recognized = 0),
            pages.observeTextStatus("a").first(),
        )
        assertFalse(checkNotNull(pages.observeTextStatus("a").first()).isRead)
    }

    @Test
    fun theStateOfADocumentsTextIsCountedFromItsPages() = runBlocking {
        document("a", pageCount = 4)
        page("a", 0, "text_status = 'EXTRACTED', text_origin = 'EMBEDDED'")
        page("a", 1, "text_status = 'EXTRACTED', text_origin = 'RECOGNIZED'")
        page("a", 2, "text_status = 'FAILED', attempts = 3")

        val read = pages.pagesOf("a")
        assertEquals(ContentOrigin.EMBEDDED, read[0].textOrigin)
        assertEquals(ContentOrigin.RECOGNIZED, read[1].textOrigin)
        assertEquals(3, read[2].attempts)
        assertEquals(
            DocumentTextStatus(pages = 4, pending = 1, failed = 1, recognized = 1),
            pages.observeTextStatus("a").first(),
        )

        page("a", 3, "text_status = 'NO_TEXT'")
        assertTrue(checkNotNull(pages.observeTextStatus("a").first()).isRead)
    }

    // A page that has no text has no origin. Compared with `=`, that would make the count of
    // recognized pages NULL for the whole document instead of 0.
    @Test
    fun aDocumentWithNoRecognizedPagesCountsZeroOfThemNotNothing() = runBlocking {
        document("a", pageCount = 2)

        assertEquals(0, pages.observeTextStatus("a").first()?.recognized)
    }

    @Test
    fun aDocumentWhosePagesWereNeverCountedHasNoneAndNoState() = runBlocking {
        document("a", pageCount = 1)
        db.execSQL("DELETE FROM pages")

        assertEquals(emptyList<Any>(), pages.pagesOf("a"))
        assertNull(pages.observeTextStatus("a").first())
        assertNull(pages.observeTextStatus("no-such-document").first())
    }

    @Test
    fun theirPagesAreTheirOwn() = runBlocking {
        document("a", pageCount = 2)
        document("b", pageCount = 5)

        assertEquals(2, pages.pagesOf("a").size)
        assertEquals(5, pages.observeTextStatus("b").first()?.pages)
    }

    private fun page(document: String, index: Int, set: String) {
        db.execSQL(
            "UPDATE pages SET $set WHERE page_index = $index " +
                "AND document_id = (SELECT id FROM documents WHERE uuid = '$document')"
        )
    }

    private suspend fun document(uuid: String, pageCount: Int) {
        documents.addScan(
            NewScan(
                uuid = uuid,
                originalName = "Scan_$uuid",
                filePath = "documents/$uuid.pdf",
                sizeBytes = 1,
                contentHash = "hash-$uuid",
                pageCount = pageCount,
                capturedAtEpochMillis = 1,
            )
        )
    }
}
