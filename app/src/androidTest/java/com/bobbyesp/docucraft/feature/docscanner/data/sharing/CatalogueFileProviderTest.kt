/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.sharing

import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScan
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * What another app sees of a document it is handed: its name, its size and its bytes.
 *
 * The provider is the app's own, registered in its manifest, and asks the app's own catalogue. So
 * this test goes through the running app's graph instead of building a database of its own: a
 * provider reading another database would prove nothing about the one that ships.
 */
@RunWith(AndroidJUnit4::class)
class CatalogueFileProviderTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val repository: DocumentsRepository = GlobalContext.get().get()

    private val file = File(context.filesDir, "documents/$Uuid.pdf")

    @Before
    fun saveADocument(): Unit = runBlocking {
        file.parentFile?.mkdirs()
        instrumentation.context.assets.open("fixtures/text-and-links.pdf").use { fixture ->
            file.outputStream().use { fixture.copyTo(it) }
        }
        repository.addScan(
            NewScan(
                uuid = Uuid,
                originalName = "Scan_20260919_142530",
                filePath = "documents/$Uuid.pdf",
                sizeBytes = file.length(),
                contentHash = "not-hashed",
                pageCount = 2,
                capturedAtEpochMillis = 1,
            )
        )
    }

    @After
    fun removeTheDocument(): Unit = runBlocking {
        runCatching { repository.deleteDocument(Uuid) }
        file.delete()
    }

    // Its file is named after its uuid, which means nothing to whoever receives it.
    @Test
    fun aSharedDocumentIsNamedAsTheCatalogueNamesItNotAsItsFile() = runBlocking {
        assertEquals("Scan_20260919_142530.pdf", displayNameOf(location()))

        repository.modifyFields(Uuid, title = "Canción de cuna", description = null)

        assertEquals("Canción de cuna.pdf", displayNameOf(location()))
    }

    // A title is free text, and what an app does with a name that is also a path is its own
    // business.
    @Test
    fun aTitleThatWouldNotBeAFileNameIsMadeSafe() = runBlocking {
        repository.modifyFields(Uuid, title = "Invoices/2026\\March.PDF", description = null)

        assertEquals("Invoices_2026_March.PDF", displayNameOf(location()))
    }

    @Test
    fun theSizeAndTheBytesAreTheFilesOwn() = runBlocking {
        val location = location()

        assertEquals(file.length(), query(location, OpenableColumns.SIZE).toLong())
        assertArrayEquals(
            file.readBytes(),
            context.contentResolver.openInputStream(location)?.use { it.readBytes() },
        )
        assertEquals("application/pdf", context.contentResolver.getType(location))
    }

    // An app that asks for one column gets that column, as from any provider.
    @Test
    fun aQueryForOnlyTheNameAnswersWithOnlyTheName() = runBlocking {
        context.contentResolver
            .query(location(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            .use { cursor ->
                checkNotNull(cursor)
                assertEquals(listOf(OpenableColumns.DISPLAY_NAME), cursor.columnNames.toList())
                cursor.moveToFirst()
                assertEquals("Scan_20260919_142530.pdf", cursor.getString(0))
            }
    }

    // A file the catalogue has no document for keeps the only name it has.
    @Test
    fun aFileTheCatalogueDoesNotKnowKeepsItsOwnName() = runBlocking {
        val stray = File(context.filesDir, "documents/stray.pdf").apply { writeText("x") }
        val location = "content://${context.packageName}.fileprovider/documents/stray.pdf".toUri()

        try {
            assertEquals("stray.pdf", displayNameOf(location))
        } finally {
            stray.delete()
        }
    }

    /** Where the catalogue says the document is opened from: the provider's URI for its file. */
    private suspend fun location(): Uri =
        (repository.getDocument(Uuid) as Document.Managed).location.value.toUri()

    private fun displayNameOf(location: Uri): String = query(location, OpenableColumns.DISPLAY_NAME)

    private fun query(location: Uri, column: String): String =
        checkNotNull(context.contentResolver.query(location, null, null, null, null)).use { cursor
            ->
            check(cursor.moveToFirst()) { "The provider answered with no row for $location" }
            cursor.getString(cursor.getColumnIndexOrThrow(column))
        }

    private companion object {
        const val Uuid = "provider-test-document"
    }
}
