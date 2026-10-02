/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner

import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.search.SearchIndex
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.DeleteDocumentUseCase
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.SaveScanDraftUseCase
import com.bobbyesp.scanner.ContentRef
import com.bobbyesp.scanner.ScanArtifact
import com.bobbyesp.scanner.ScanDraft
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * A scan from the moment the scanner hands it over to the moment it is deleted, through the app's
 * own graph: the real use cases, the real storage, the real catalogue and the real provider. Only
 * the scanner is missing, which needs a camera; what it produces, a PDF somewhere temporary, is
 * stood in for by a test PDF.
 */
@RunWith(AndroidJUnit4::class)
class SaveAndDeleteScanTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val koin = GlobalContext.get()
    private val saveScan: SaveScanDraftUseCase = koin.get()
    private val deleteDocument: DeleteDocumentUseCase = koin.get()
    private val repository: DocumentsRepository = koin.get()
    private val searchIndex: SearchIndex = koin.get()
    private val database: DocumentsDatabase = koin.get()

    /** Where the scanner would leave its PDF. */
    private val scanned = File(context.cacheDir, "save-test-scan.pdf")
    private val saved = mutableListOf<String>()

    @After
    fun cleanUp(): Unit = runBlocking {
        scanned.delete()
        for (uuid in saved) {
            runCatching { repository.deleteDocument(uuid) }
            File(context.filesDir, "documents/$uuid.pdf").delete()
        }
    }

    @Test
    fun aSavedScanIsAFileNamedByItsUuidAndADocumentThatCanBeOpened() = runBlocking {
        val document = save(reportedPages = 3)

        val file = File(context.filesDir, "documents/${document.uuid}.pdf")
        assertEquals("documents/${document.uuid}.pdf", document.filePath)
        assertArrayEquals(scanned.readBytes(), file.readBytes())
        assertEquals(scanned.length(), document.sizeBytes)
        assertEquals(3, document.pageCount)

        // What the viewer and other apps are given: the provider's URI, which has to open.
        val location = document.location.value.toUri()
        assertArrayEquals(
            scanned.readBytes(),
            context.contentResolver.openInputStream(location)?.use { it.readBytes() },
        )
        context.contentResolver.query(location, null, null, null, null).use { cursor ->
            checkNotNull(cursor).moveToFirst()
            val name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            assertEquals("${document.originalName}.pdf", name)
        }
    }

    @Test
    fun aSavedScanHasItsActivityAndAPageForEachPage() = runBlocking {
        val document = save(reportedPages = 3)

        assertEquals(
            listOf("SCAN|3|AVAILABLE|3|3"),
            rows(
                "SELECT d.origin, d.page_count, a.availability, s.pages, s.pending " +
                    "FROM documents d " +
                    "JOIN document_activity a ON a.document_id = d.id " +
                    "JOIN document_text_status s ON s.document_id = d.id " +
                    "WHERE d.uuid = '${document.uuid}'"
            ),
        )
    }

    // A scanner that does not say how many pages it captured: they are counted in the file.
    @Test
    fun theAppCountsThePagesWhenTheScannerDoesNot() = runBlocking {
        val document = save(reportedPages = 0)

        assertEquals(3, document.pageCount)
    }

    @Test
    fun aSavedScanIsFoundByItsNameStraightAway() = runBlocking {
        val document = save(reportedPages = 3)

        assertTrue(searchIndex.search("scan").any { it.documentUuid == document.uuid })
    }

    @Test
    fun deletingTakesTheFileAndEverythingTheCatalogueKept() = runBlocking {
        val document = save(reportedPages = 3)
        val file = File(context.filesDir, document.filePath)

        deleteDocument(document)

        assertFalse(file.exists())
        assertEquals(
            listOf("0|0|0"),
            rows(
                "SELECT (SELECT COUNT(*) FROM documents WHERE uuid = '${document.uuid}'), " +
                    "(SELECT COUNT(*) FROM pages p JOIN documents d ON d.id = p.document_id " +
                    "WHERE d.uuid = '${document.uuid}'), " +
                    "(SELECT COUNT(*) FROM document_activity a JOIN documents d " +
                    "ON d.id = a.document_id WHERE d.uuid = '${document.uuid}')"
            ),
        )
    }

    private suspend fun save(reportedPages: Int): Document.Managed {
        instrumentation.context.assets.open("fixtures/text-and-links.pdf").use { fixture ->
            scanned.outputStream().use { fixture.copyTo(it) }
        }
        val draft =
            ScanDraft(
                artifacts =
                    listOf(ScanArtifact.Pdf(ContentRef(scanned.toUri().toString()), reportedPages)),
                capturedAtEpochMillis = System.currentTimeMillis(),
            )

        val uuid = saveScan(draft).getOrThrow()
        saved += uuid
        return repository.getDocument(uuid) as Document.Managed
    }

    private fun rows(sql: String): List<String> =
        database.openHelper.readableDatabase.query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).joinToString("|") { cursor.getString(it) })
                }
            }
        }
}
