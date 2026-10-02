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
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentLocations
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScannedDocument
import com.bobbyesp.scanner.ContentRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The catalogue against a real database. What a query matches, and what goes with a deleted row,
 * are decided by the device's SQLite and how it was compiled, which a JVM test cannot stand in for.
 */
@RunWith(AndroidJUnit4::class)
class LocalDocumentsRepositoryImplTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database =
        Room.inMemoryDatabaseBuilder(context, DocumentsDatabase::class.java)
            .addCallback(DatabaseTriggers.CreateOnNewDatabase)
            .build()
    private val repository =
        LocalDocumentsRepositoryImpl(
            documentDao = database.documentDao(),
            locations = DocumentLocations(context),
            now = { Now },
        )

    @After
    fun closeDatabase() {
        database.close()
    }

    // --- search ---

    // Android's SQLite reads `AND` as a word to look for, not as an operator: terms joined with it
    // only matched documents that also contained "and".
    @Test
    fun aQueryOfSeveralWordsFindsTheDocumentThatHasThemAll() = runBlocking {
        save("Factura luz marzo", capturedAt = 1)
        save("Factura agua marzo", capturedAt = 2)

        assertEquals(listOf("Factura luz marzo"), search("factura luz"))
    }

    @Test
    fun theWordsOfAQueryCanComeInAnyOrder() = runBlocking {
        save("Factura luz marzo", capturedAt = 1)

        assertEquals(listOf("Factura luz marzo"), search("marzo factura"))
    }

    // A space between terms means "all of them", not "any of them".
    @Test
    fun aDocumentMissingOneOfTheWordsIsNotFound() = runBlocking {
        save("Factura luz marzo", capturedAt = 1)

        assertEquals(emptyList<String>(), search("factura gas"))
    }

    @Test
    fun eachWordMatchesAsAPrefix() = runBlocking {
        save("Factura luz marzo", capturedAt = 1)
        save("Contrato alquiler", capturedAt = 2)

        assertEquals(listOf("Factura luz marzo"), search("fac lu"))
    }

    // The index folds accents and case, whichever side has them: what is typed or what is stored.
    @Test
    fun accentsAndCaseMakeNoDifference() = runBlocking {
        save("Scan_1", capturedAt = 1)
        save("Scan_2", capturedAt = 2)
        repository.modifyFields(uuidOf("Scan_1"), title = "Canción de cuna", description = null)
        repository.modifyFields(uuidOf("Scan_2"), title = "Recibo", description = "Del niño")

        assertEquals(listOf("Scan_1"), search("cancion"))
        assertEquals(listOf("Scan_1"), search("CANCIÓN"))
        assertEquals(listOf("Scan_2"), search("nino"))
        assertEquals(listOf("Scan_2"), search("Niño"))
    }

    // The index follows the table: the old title stops matching as soon as it is replaced.
    @Test
    fun anEditedTitleIsWhatSearchFinds() = runBlocking {
        save("Scan_1", capturedAt = 1)
        repository.modifyFields(uuidOf("Scan_1"), title = "Contrato", description = null)
        repository.modifyFields(uuidOf("Scan_1"), title = "Factura", description = null)

        assertEquals(emptyList<String>(), search("contrato"))
        assertEquals(listOf("Scan_1"), search("factura"))
    }

    // --- saving ---

    // A document without its activity would be missing from Recents, and one without its pages
    // would never have its text read.
    @Test
    fun aSavedDocumentHasItsActivityAndAPageForEachOfItsPages() = runBlocking {
        save("Scan_1", capturedAt = 5, pageCount = 3)

        val db = database.openHelper.writableDatabase
        assertEquals(
            listOf("MANAGED|SCAN|scans/pdf/Scan_1.pdf|3|5|$Now"),
            db.rows(
                "SELECT custody, origin, file_path, page_count, captured_at, created_at " +
                    "FROM documents"
            ),
        )
        assertEquals(
            listOf("null|$Now|AVAILABLE"),
            db.rows("SELECT last_opened_at, last_activity_at, availability FROM document_activity"),
        )
        assertEquals(
            listOf("0|PENDING", "1|PENDING", "2|PENDING"),
            db.rows("SELECT page_index, text_status FROM pages ORDER BY page_index"),
        )
    }

    // A scanner that does not report its pages reports none. Recorded as zero pages, the document
    // would be rejected by the rules of the catalogue; it is recorded as not known.
    @Test
    fun aScanWhosePagesWereNotReportedIsSavedWithAnUnknownPageCount() = runBlocking {
        save("Scan_1", capturedAt = 1, pageCount = 0)

        val db = database.openHelper.writableDatabase
        assertEquals(listOf("null"), db.rows("SELECT page_count FROM documents"))
        assertEquals(0, db.long("SELECT COUNT(*) FROM pages"))
        assertEquals(0, repository.observeDocuments().first().single().pageCount)
    }

    // Replacing on conflict would delete the document that already has that file.
    @Test
    fun savingOverTheFileOfAnotherDocumentFailsAndKeepsThatDocument() = runBlocking {
        save("Scan_1", capturedAt = 1)
        val first = uuidOf("Scan_1")

        assertThrows(Exception::class.java) { runBlocking { save("Scan_1", capturedAt = 2) } }

        assertEquals(listOf(first), repository.observeDocuments().first().map { it.uuid })
    }

    // --- reading back ---

    // The catalogue keeps a relative path; what the app opens is the provider's URI for it.
    @Test
    fun aDocumentIsReadBackWithALocationThatCanBeOpened() = runBlocking {
        save("Factura luz marzo", capturedAt = 1)

        val document = repository.observeDocuments().first().single()

        assertEquals(
            "content://${context.packageName}.fileprovider/scanned-pdfs/Factura%20luz%20marzo.pdf",
            document.location.value,
        )
        assertEquals(document, repository.getDocument(document.uuid))
    }

    // --- deleting ---

    @Test
    fun aDeletedDocumentTakesItsPagesAndItsActivityAndIsNoLongerFound() = runBlocking {
        save("Factura luz", capturedAt = 1, pageCount = 2)
        save("Contrato", capturedAt = 2, pageCount = 1)
        val bill = repository.observeDocuments().first().first { it.filename == "Factura luz" }

        repository.deleteDocument(bill.location)

        val db = database.openHelper.writableDatabase
        assertEquals(listOf("Contrato"), repository.observeDocuments().first().map { it.filename })
        assertEquals(1, db.long("SELECT COUNT(*) FROM pages"))
        assertEquals(1, db.long("SELECT COUNT(*) FROM document_activity"))
        assertEquals(emptyList<String>(), search("factura"))
    }

    // A document whose file is not where the provider serves files, as the migration leaves the
    // earlier of two scans that shared a file. It is listed, and it has to be removable.
    @Test
    fun aDocumentWhoseFileIsMissingIsListedAndCanBeDeleted() = runBlocking {
        save("Scan_1", capturedAt = 1)
        val db = database.openHelper.writableDatabase
        db.execSQL("UPDATE documents SET file_path = 'missing/gone.pdf'")

        val document = repository.observeDocuments().first().single()
        assertEquals("file://${context.filesDir.path}/missing/gone.pdf", document.location.value)

        repository.deleteDocument(document.location)

        assertEquals(0, db.long("SELECT COUNT(*) FROM documents"))
    }

    // Deleting by location must never reach a document it was not asked about.
    @Test
    fun aLocationThatIsNotADocumentOfTheCatalogueDeletesNothing() = runBlocking {
        save("Scan_1", capturedAt = 1)

        for (location in
            listOf("file:///sdcard/Download/Scan_1.pdf", "content://other/Scan_1.pdf")) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.deleteDocument(ContentRef(location)) }
            }
        }

        assertEquals(1, repository.observeDocuments().first().size)
    }

    private suspend fun save(filename: String, capturedAt: Long, pageCount: Int = 1) {
        repository.saveDocument(
            NewScannedDocument(
                filename = filename,
                location =
                    ContentRef(
                        "content://${context.packageName}.fileprovider/scanned-pdfs/$filename.pdf"
                    ),
                capturedAtEpochMillis = capturedAt,
                sizeBytes = 1,
                pageCount = pageCount,
                thumbnail = null,
            )
        )
    }

    private suspend fun search(query: String): List<String> =
        repository.searchDocuments(query).map { it.filename }

    private suspend fun uuidOf(filename: String): String =
        repository.observeDocuments().first().first { it.filename == filename }.uuid

    private companion object {
        const val Now = 1_800_000_000_000
    }
}
