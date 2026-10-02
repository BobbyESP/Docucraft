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
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScan
import com.bobbyesp.scanner.ContentRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The catalogue against a real database. What a query matches, and what goes with a deleted row,
 * are decided by the device's SQLite and how it was compiled, which a JVM test cannot stand in for.
 */
@RunWith(AndroidJUnit4::class)
class DocumentsRepositoryImplTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database =
        Room.inMemoryDatabaseBuilder(context, DocumentsDatabase::class.java)
            .addCallback(DatabaseTriggers.CreateOnNewDatabase)
            .build()
    private val repository =
        DocumentsRepositoryImpl(
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
            listOf("uuid-Scan_1|MANAGED|SCAN|documents/uuid-Scan_1.pdf|hash-Scan_1|3|5|$Now"),
            db.rows(
                "SELECT uuid, custody, origin, file_path, content_hash, page_count, " +
                    "captured_at, created_at FROM documents"
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

    // An old scan can be in the catalogue without a page count. It is read as not known, which is
    // not the same as having none.
    @Test
    fun aDocumentWhosePagesWereNeverCountedIsReadWithAnUnknownPageCount() = runBlocking {
        save("Scan_1", capturedAt = 1)
        database.openHelper.writableDatabase.execSQL("UPDATE documents SET page_count = NULL")

        assertNull(repository.observeDocuments().first().single().pageCount)
    }

    // Replacing on conflict would delete the document that already has that uuid or that file.
    @Test
    fun aScanThatClaimsAnotherDocumentsUuidOrFileIsRefusedAndThatDocumentIsKept() = runBlocking {
        save("Scan_1", capturedAt = 1)

        assertThrows(Exception::class.java) {
            runBlocking { save("Scan_2", capturedAt = 2, uuid = "uuid-Scan_1") }
        }
        assertThrows(Exception::class.java) {
            runBlocking { save("Scan_3", capturedAt = 3, filePath = "documents/uuid-Scan_1.pdf") }
        }

        assertEquals(
            listOf("uuid-Scan_1|Scan_1"),
            database.openHelper.writableDatabase.rows("SELECT uuid, original_name FROM documents"),
        )
    }

    // The three rows are written together. A document left without its pages by a failure half
    // way would never have its text read, and nothing would say so.
    @Test
    fun aScanThatCannotBeAddedLeavesNothingOfItselfBehind() = runBlocking {
        save("Scan_1", capturedAt = 1, pageCount = 2)
        val db = database.openHelper.writableDatabase

        assertThrows(Exception::class.java) {
            runBlocking { save("Scan_2", capturedAt = 2, pageCount = 5, uuid = "uuid-Scan_1") }
        }

        assertEquals(1, db.long("SELECT COUNT(*) FROM documents"))
        assertEquals(1, db.long("SELECT COUNT(*) FROM document_activity"))
        assertEquals(2, db.long("SELECT COUNT(*) FROM pages"))
    }

    // --- reading back ---

    // The catalogue keeps a relative path; what the app opens is the provider's URI for it.
    @Test
    fun aDocumentIsReadBackWithALocationThatCanBeOpened() = runBlocking {
        save("Scan_1", capturedAt = 1)

        val document = repository.observeDocuments().first().single()

        assertEquals("documents/uuid-Scan_1.pdf", document.filePath)
        assertEquals(
            "content://${context.packageName}.fileprovider/documents/uuid-Scan_1.pdf",
            document.location.value,
        )
        assertEquals(document, repository.getDocument(document.uuid))
    }

    // Documents saved before files were named by uuid stay where they were, under their old name.
    @Test
    fun aDocumentSavedBeforeFilesWereNamedByUuidIsStillOpenedFromWhereItIs() = runBlocking {
        save("Factura luz marzo", capturedAt = 1, filePath = "scans/pdf/Factura luz marzo.pdf")

        assertEquals(
            "content://${context.packageName}.fileprovider/scanned-pdfs/Factura%20luz%20marzo.pdf",
            repository.observeDocuments().first().single().location.value,
        )
    }

    // The viewer is handed the title, the suggested title or the original name, in that order.
    @Test
    fun aSuggestedTitleNamesADocumentOnlyUntilTheUserWritesOne() = runBlocking {
        save("Scan_1", capturedAt = 1)
        val db = database.openHelper.writableDatabase

        assertEquals("Scan_1", repository.observeDocuments().first().single().name)

        db.execSQL("UPDATE documents SET suggested_title = 'Invoice 42'")
        assertEquals("Invoice 42", repository.observeDocuments().first().single().name)

        repository.modifyFields(uuidOf("Scan_1"), title = "Electricity", description = null)
        assertEquals("Electricity", repository.observeDocuments().first().single().name)
    }

    // Another app's document is known to the catalogue, and can be read by its uuid, but it is not
    // part of the library: it is not listed and search does not find it.
    @Test
    fun aLinkedDocumentIsReadAsOneAndStaysOutOfTheLibrary() = runBlocking {
        save("Factura luz", capturedAt = 1)
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO documents (uuid, custody, original_name, mime_type, size_bytes, " +
                "is_encrypted, is_favorite, ocr_enabled, uri, has_persisted_permission, " +
                "created_at, updated_at, content_updated_at) " +
                "VALUES ('linked-1', 'LINKED', 'Factura gas.pdf', 'application/pdf', 512, " +
                "0, 0, 0, 'content://other.app/documents/7', 1, 5, 5, 5)"
        )

        val linked = repository.getDocument("linked-1")

        assertEquals(
            Document.Linked(
                uuid = "linked-1",
                originalName = "Factura gas.pdf",
                title = null,
                suggestedTitle = null,
                description = null,
                location = ContentRef("content://other.app/documents/7"),
                sizeBytes = 512,
                pageCount = null,
                createdAtEpochMillis = 5,
                hasPersistedPermission = true,
            ),
            linked,
        )
        assertEquals(
            listOf("Factura luz"),
            repository.observeDocuments().first().map { it.originalName },
        )
        assertEquals(listOf("Factura luz"), search("factura"))
    }

    // --- deleting ---

    @Test
    fun aDeletedDocumentTakesItsPagesAndItsActivityAndIsNoLongerFound() = runBlocking {
        save("Factura luz", capturedAt = 1, pageCount = 2)
        save("Contrato", capturedAt = 2, pageCount = 1)
        val bill = repository.observeDocuments().first().first { it.originalName == "Factura luz" }

        repository.deleteDocument(bill.uuid)

        val db = database.openHelper.writableDatabase
        assertEquals(
            listOf("Contrato"),
            repository.observeDocuments().first().map { it.originalName },
        )
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

        repository.deleteDocument(document.uuid)

        assertEquals(0, db.long("SELECT COUNT(*) FROM documents"))
    }

    // Two entries can point at one file. Deleting one of them must leave the other.
    @Test
    fun deletingADocumentLeavesAnotherOneThatIsStoredInTheSamePlaceAlone() = runBlocking {
        save("Scan_1", capturedAt = 1)
        save("Scan_2", capturedAt = 2)
        val db = database.openHelper.writableDatabase
        db.execSQL(
            "UPDATE documents SET file_path = 'missing/gone.pdf' WHERE original_name = 'Scan_2'"
        )

        repository.deleteDocument(uuidOf("Scan_2"))

        assertEquals(
            listOf("Scan_1"),
            repository.observeDocuments().first().map { it.originalName },
        )
    }

    @Test
    fun deletingADocumentTheCatalogueDoesNotHaveIsReportedAndDeletesNothing() = runBlocking {
        save("Scan_1", capturedAt = 1)

        assertThrows(NoSuchElementException::class.java) {
            runBlocking { repository.deleteDocument("no-such-document") }
        }

        assertEquals(1, repository.observeDocuments().first().size)
    }

    private suspend fun save(
        name: String,
        capturedAt: Long,
        pageCount: Int = 1,
        uuid: String = "uuid-$name",
        filePath: String = "documents/$uuid.pdf",
    ) {
        repository.addScan(
            NewScan(
                uuid = uuid,
                originalName = name,
                filePath = filePath,
                sizeBytes = 1,
                contentHash = "hash-$name",
                pageCount = pageCount,
                capturedAtEpochMillis = capturedAt,
            )
        )
    }

    private suspend fun search(query: String): List<String> =
        repository.searchDocuments(query).map { it.originalName }

    private suspend fun uuidOf(filename: String): String =
        repository.observeDocuments().first().first { it.originalName == filename }.uuid

    private companion object {
        const val Now = 1_800_000_000_000
    }
}
