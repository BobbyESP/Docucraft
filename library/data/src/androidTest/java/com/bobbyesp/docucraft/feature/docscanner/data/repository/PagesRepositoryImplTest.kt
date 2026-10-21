/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bobbyesp.docucraft.feature.docscanner.data.db.DatabaseTriggers
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.search.Fts4SearchIndex
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentLocations
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScan
import com.bobbyesp.docucraft.feature.docscanner.domain.model.PageTextStatus
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentTextStatus
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.PageTextRecord
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.TextLine
import com.bobbyesp.documentcontent.TextWord
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
    private val pages = PagesRepositoryImpl(database.pageDao(), now = { 5_000L })
    private val index = Fts4SearchIndex(database.searchDao())
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

    // --- reading ---

    @Test
    fun aPageWhoseTextIsWrittenDownIsReadAndItsDocumentIsFoundByIt() = runBlocking {
        document("a", pageCount = 2)

        assertTrue(pages.storeText("a", 1, embedded("Contrato de alquiler"), extractorVersion = 1))

        assertEquals(listOf(0), pages.pagesToRead("a"))
        assertEquals(ContentOrigin.EMBEDDED, pages.pagesOf("a")[1].textOrigin)
        assertEquals(PageTextStatus.EXTRACTED, pages.pagesOf("a")[1].textStatus)
        assertEquals(listOf("a"), found("alquiler"))
        assertEquals(
            listOf("EXTRACTED|EMBEDDED|platform|1|5000"),
            rows(
                "SELECT text_status, text_origin, engine, extractor_version, extracted_at " +
                    "FROM pages WHERE page_index = 1"
            ),
        )
    }

    // Replacing the row would not tell the full-text index, which would go on finding the old
    // words.
    @Test
    fun aPageReadAgainIsFoundByItsNewTextAndNoLongerByTheOld() = runBlocking {
        document("a", pageCount = 1)
        pages.storeText("a", 0, embedded("factura de la luz"), extractorVersion = 1)

        pages.storeText("a", 0, embedded("recibo del agua"), extractorVersion = 2)

        assertEquals(emptyList<String>(), found("factura"))
        assertEquals(listOf("a"), found("recibo"))
        assertEquals(listOf("1"), rows("SELECT COUNT(*) FROM page_texts"))
    }

    @Test
    fun aPageReadWithoutTextKeepsNoneAndSaysWhy() = runBlocking {
        document("a", pageCount = 1)
        pages.storeText("a", 0, embedded("factura"), extractorVersion = 1)

        assertTrue(pages.storeWithoutText("a", 0, PageTextStatus.OCR_DISABLED, 1))

        assertEquals(PageTextStatus.OCR_DISABLED, pages.pagesOf("a").single().textStatus)
        assertNull(pages.pagesOf("a").single().textOrigin)
        assertEquals(emptyList<String>(), found("factura"))
        assertEquals(emptyList<Int>(), pages.pagesToRead("a"))
    }

    @Test
    fun aPageThatIsNotThereIsNotWrittenTo() = runBlocking {
        document("a", pageCount = 1)

        assertFalse(pages.storeText("a", 7, embedded("x"), 1))
        assertFalse(pages.storeText("gone", 0, embedded("x"), 1))
        assertFalse(pages.storeWithoutText("gone", 0, PageTextStatus.OCR_DISABLED, 1))
        assertNull(pages.recordFailure("gone", 0, maxAttempts = 3))
        assertEquals(listOf("0"), rows("SELECT COUNT(*) FROM page_texts"))
    }

    @Test
    fun aPageIsGivenUpOnWhenItHasFailedAsOftenAsItIsTried() = runBlocking {
        document("a", pageCount = 1)

        assertEquals(PageTextStatus.PENDING, pages.recordFailure("a", 0, maxAttempts = 3))
        assertEquals(PageTextStatus.PENDING, pages.recordFailure("a", 0, maxAttempts = 3))
        assertEquals(PageTextStatus.FAILED, pages.recordFailure("a", 0, maxAttempts = 3))

        assertEquals(3, pages.pagesOf("a").single().attempts)
        assertEquals(emptyList<Int>(), pages.pagesToRead("a"))
    }

    @Test
    fun readingAPageForgetsItsEarlierFailures() = runBlocking {
        document("a", pageCount = 1)
        pages.recordFailure("a", 0, maxAttempts = 3)

        pages.storeText("a", 0, embedded("x"), 1)

        assertEquals(0, pages.pagesOf("a").single().attempts)
    }

    // --- recognized text ---

    @Test
    fun recognizedTextIsKeptWithWhereItsWordsAreAndComesBackAsItWas() = runBlocking {
        document("a", pageCount = 1)

        pages.storeText("a", 0, recognized("Factura", "2026"), extractorVersion = 1)

        val text = checkNotNull(pages.recognizedText("a", 0))
        assertEquals(ContentOrigin.RECOGNIZED, text.origin)
        assertEquals(0.8f, text.confidence)
        assertEquals("mlkit-latin", text.engine)
        assertEquals("es", text.language)
        assertEquals(listOf("Factura", "2026"), text.lines.single().words.map { it.text })
        assertEquals(NormalizedRect(0.1f, 0.2f, 0.3f, 0.4f), text.lines.single().words[0].bounds)
        assertEquals(listOf("a"), found("factura"))
        assertEquals(listOf("1"), rows("SELECT recognized FROM document_text_status"))
    }

    // A PDF's own text is read from the PDF whenever it is needed.
    @Test
    fun aPageThatWasNotRecognizedHasNoRecognizedText() = runBlocking {
        document("a", pageCount = 2)
        pages.storeText("a", 0, embedded("typed"), extractorVersion = 1)

        assertNull(pages.recognizedText("a", 0))
        assertNull(pages.recognizedText("a", 1))
        assertNull(pages.recognizedText("gone", 0))
    }

    @Test
    fun readingARecognizedPageAgainReplacesWhereItsWordsWere() = runBlocking {
        document("a", pageCount = 1)
        pages.storeText("a", 0, recognized("old"), extractorVersion = 1)

        pages.storeText("a", 0, recognized("new", "words"), extractorVersion = 1)

        assertEquals(
            listOf("new", "words"),
            pages.recognizedText("a", 0)?.lines?.single()?.words?.map { it.text },
        )
        assertEquals(listOf("1"), rows("SELECT COUNT(*) FROM page_layouts"))
    }

    @Test
    fun turningRecognitionOnMakesTheWaitingPagesPendingAndNoOthers() = runBlocking {
        document("a", pageCount = 3)
        pages.storeText("a", 0, embedded("typed"), extractorVersion = 1)
        pages.storeWithoutText("a", 1, PageTextStatus.OCR_DISABLED, extractorVersion = 1)
        pages.storeWithoutText("a", 2, PageTextStatus.OCR_DISABLED, extractorVersion = 1)

        assertTrue(pages.setTextRecognition("a", enabled = true))

        assertEquals(listOf(1, 2), pages.pagesToRead("a"))
        assertEquals(listOf("1"), rows("SELECT ocr_enabled FROM documents"))
        assertEquals(listOf("a"), found("typed"))
    }

    // The words of an image are only there because recognition was asked for.
    @Test
    fun turningRecognitionOffForgetsWhatWasRecognizedAndKeepsTheDocumentsOwnText() = runBlocking {
        document("a", pageCount = 3)
        pages.setTextRecognition("a", enabled = true)
        pages.storeText("a", 0, embedded("typed"), extractorVersion = 1)
        pages.storeText("a", 1, recognized("scanned"), extractorVersion = 1)
        pages.storeWithoutText("a", 2, PageTextStatus.NO_TEXT, extractorVersion = 1)

        assertTrue(pages.setTextRecognition("a", enabled = false))

        assertEquals(listOf("a"), found("typed"))
        assertEquals(emptyList<String>(), found("scanned"))
        assertNull(pages.recognizedText("a", 1))
        assertEquals(
            listOf(
                PageTextStatus.EXTRACTED,
                PageTextStatus.OCR_DISABLED,
                PageTextStatus.OCR_DISABLED,
            ),
            pages.pagesOf("a").map { it.textStatus },
        )
        assertEquals(
            listOf("1|0"),
            rows("SELECT COUNT(*), (SELECT COUNT(*) FROM page_layouts) FROM page_texts"),
        )
        assertEquals(listOf("0"), rows("SELECT ocr_enabled FROM documents"))
    }

    // Another app's document has no pages, and the table refuses recognition for it.
    @Test
    fun recognitionIsOnlySetForADocumentTheAppKeeps() = runBlocking {
        db.execSQL(
            """INSERT INTO documents (uuid, custody, original_name, mime_type, is_encrypted,
                is_favorite, ocr_enabled, uri, has_persisted_permission, created_at, updated_at,
                content_updated_at)
            VALUES ('linked', 'LINKED', 'shared', 'application/pdf', 0, 0, 0, 'content://x', 0, 1,
                1, 1)"""
        )

        assertFalse(pages.setTextRecognition("linked", enabled = true))
        assertFalse(pages.setTextRecognition("gone", enabled = true))
        assertEquals(listOf("0"), rows("SELECT ocr_enabled FROM documents"))
    }

    // Read just as recognition was being turned on, and left waiting by mistake.
    @Test
    fun aPageLeftWaitingInADocumentThatHasRecognitionOnIsReadAgain() = runBlocking {
        document("on", pageCount = 1)
        document("off", pageCount = 1)
        pages.setTextRecognition("on", enabled = true)
        pages.storeWithoutText("on", 0, PageTextStatus.OCR_DISABLED, extractorVersion = 1)
        pages.storeWithoutText("off", 0, PageTextStatus.OCR_DISABLED, extractorVersion = 1)

        pages.requeue(extractorVersion = 1)

        assertEquals(listOf(0), pages.pagesToRead("on"))
        assertEquals(emptyList<Int>(), pages.pagesToRead("off"))
    }

    // --- picking up where it was left ---

    @Test
    fun failedPagesAndPagesReadByAnOlderExtractorBecomePendingAgain() = runBlocking {
        document("a", pageCount = 4)
        pages.storeText("a", 0, embedded("old"), extractorVersion = 1)
        pages.storeText("a", 1, embedded("current"), extractorVersion = 2)
        pages.storeWithoutText("a", 2, PageTextStatus.OCR_DISABLED, extractorVersion = 1)
        repeat(3) { pages.recordFailure("a", 3, maxAttempts = 3) }

        pages.requeue(extractorVersion = 2)

        assertEquals(listOf(0, 2, 3), pages.pagesToRead("a"))
        assertEquals(0, pages.pagesOf("a")[3].attempts)
        // Still found by what it said, until it is read again.
        assertEquals(listOf("a"), found("old"))
    }

    @Test
    fun theDocumentsToReadAreTheOnesOfTheLibraryWithAPagePending() = runBlocking {
        document("read", pageCount = 1)
        pages.storeText("read", 0, embedded("x"), 1)
        document("unread", pageCount = 2)
        document("binned", pageCount = 1)
        db.execSQL("UPDATE documents SET trashed_at = 9 WHERE uuid = 'binned'")

        assertEquals(listOf("unread"), pages.documentsToRead())
    }

    // --- pages that were never counted ---

    // A document from an older catalogue that did not say how many pages it had.
    @Test
    fun aDocumentWhosePagesWereNeverCountedIsToBeReadAndGetsItsPages() = runBlocking {
        document("a", pageCount = 1)
        db.execSQL("DELETE FROM pages")
        db.execSQL("UPDATE documents SET page_count = NULL")
        assertEquals(listOf("a"), pages.documentsToRead())

        assertTrue(pages.createPages("a", 3))

        assertEquals(listOf(0, 1, 2), pages.pagesToRead("a"))
        assertEquals(listOf("3"), rows("SELECT page_count FROM documents"))
    }

    @Test
    fun pagesAreNotCreatedTwiceNorForNoPages() = runBlocking {
        document("a", pageCount = 2)

        assertFalse(pages.createPages("a", 5))
        assertFalse(pages.createPages("gone", 5))
        db.execSQL("DELETE FROM pages")
        db.execSQL("UPDATE documents SET page_count = NULL")
        assertFalse(pages.createPages("a", 0))

        assertEquals(emptyList<Any>(), pages.pagesOf("a"))
    }

    private fun embedded(text: String) =
        PageTextRecord(text, ContentOrigin.EMBEDDED, confidence = null, engine = "platform")

    private fun recognized(vararg words: String) =
        PageTextRecord(
            text = words.joinToString(" "),
            origin = ContentOrigin.RECOGNIZED,
            confidence = 0.8f,
            engine = "mlkit-latin",
            language = "es",
            layout =
                listOf(
                    TextLine(words.map { TextWord(it, NormalizedRect(0.1f, 0.2f, 0.3f, 0.4f)) })
                ),
        )

    private suspend fun found(query: String): List<String> =
        index.search(query).map { it.documentUuid }

    private fun rows(sql: String): List<String> =
        db.query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).joinToString("|") { cursor.getString(it) })
                }
            }
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
