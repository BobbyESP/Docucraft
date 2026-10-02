/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.storage

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bobbyesp.docucraft.feature.docscanner.data.service.DocumentOperationsServiceImpl
import com.bobbyesp.docucraft.feature.docscanner.domain.exception.ScanSaveException
import com.bobbyesp.scanner.ContentRef
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Storage against the real file system and the platform's PDF reader: where a file ends up, that it
 * is whole, and that a save which does not finish leaves nothing behind.
 */
@RunWith(AndroidJUnit4::class)
class DocumentStorageImplTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val storage = DocumentStorageImpl(context, DocumentOperationsServiceImpl(context))

    private val documents = File(context.filesDir, "documents")

    /** What a scanner would hand over: a file somewhere that is not the app's storage. */
    private val incoming = File(context.cacheDir, "storage-test-incoming")

    @Before
    fun startEmpty() {
        documents.deleteRecursively()
        incoming.deleteRecursively()
        incoming.mkdirs()
    }

    @After
    fun cleanUp() {
        documents.deleteRecursively()
        incoming.deleteRecursively()
    }

    @Test
    fun aDocumentIsStoredWholeUnderTheNameOfItsUuid() = runBlocking {
        val source = fixture("text-and-links.pdf")

        val stored = storage.storeDocument(source.asRef(), documentUuid = "3f2c8a1e")

        assertEquals("documents/3f2c8a1e.pdf", stored.filePath)
        assertArrayEquals(source.readBytes(), File(context.filesDir, stored.filePath).readBytes())
    }

    // The catalogue keeps these, so they have to be the stored file's and not the scanner's word.
    @Test
    fun whatIsLearntOfTheFileIsItsSizeItsHashAndItsPages() = runBlocking {
        val source = fixture("text-and-links.pdf")

        val stored = storage.storeDocument(source.asRef(), documentUuid = "a")

        assertEquals(source.length(), stored.sizeBytes)
        assertEquals(sha256(source), stored.contentHash)
        assertEquals(3, stored.pageCount)
    }

    // The name comes from the uuid, so nothing a document is called can make two files collide.
    @Test
    fun twoDocumentsNeverShareAFile() = runBlocking {
        val first = storage.storeDocument(fixture("text-and-links.pdf").asRef(), "first")
        val second = storage.storeDocument(fixture("scanned-image-only.pdf").asRef(), "second")

        assertEquals(setOf("first.pdf", "second.pdf"), documents.list()?.toSet())
        assertFalse(first.contentHash == second.contentHash)
    }

    // A file with its final name is a whole document. What is still being written has another.
    @Test
    fun nothingUnfinishedIsLeftBesideAStoredDocument() = runBlocking {
        storage.storeDocument(fixture("text-and-links.pdf").asRef(), documentUuid = "a")

        assertEquals(listOf("a.pdf"), documents.list()?.toList())
    }

    @Test
    fun anEmptyDocumentIsRefusedAndLeavesNothing() {
        val empty = File(incoming, "empty.pdf").apply { writeBytes(ByteArray(0)) }

        assertThrows(ScanSaveException.OutputFileEmpty::class.java) {
            runBlocking { storage.storeDocument(empty.asRef(), documentUuid = "a") }
        }

        assertEquals(emptyList<String>(), documents.list().orEmpty().toList())
    }

    @Test
    fun aSourceThatCannotBeReadIsReportedAndLeavesNothing() {
        val gone = File(incoming, "never-written.pdf")

        assertThrows(ScanSaveException.OutputFileNotCopied::class.java) {
            runBlocking { storage.storeDocument(gone.asRef(), documentUuid = "a") }
        }

        assertEquals(emptyList<String>(), documents.list().orEmpty().toList())
    }

    // Storage copies bytes; whether they are a document is for the caller to judge from the pages.
    @Test
    fun aFileThatIsNotAPdfIsStoredWithNoPages() = runBlocking {
        val text = File(incoming, "notes.pdf").apply { writeText("This is not a PDF.") }

        val stored = storage.storeDocument(text.asRef(), documentUuid = "a")

        assertNull(stored.pageCount)
        assertTrue(File(context.filesDir, stored.filePath).exists())
    }

    @Test
    fun aProtectedPdfIsStoredWithNoPages() = runBlocking {
        val stored =
            storage.storeDocument(fixture("password-protected.pdf").asRef(), documentUuid = "a")

        assertNull(stored.pageCount)
    }

    @Test
    fun deletingRemovesTheFileAndDeletingAgainIsNotAnError() = runBlocking {
        val stored = storage.storeDocument(fixture("text-and-links.pdf").asRef(), "a")

        storage.delete(stored.filePath)
        storage.delete(stored.filePath)

        assertFalse(File(context.filesDir, stored.filePath).exists())
    }

    // The path comes from the catalogue. Whatever it says, only the app's own files are storage's
    // to remove.
    @Test
    fun aPathThatLeavesTheFilesDirectoryDeletesNothing() = runBlocking {
        val outside = File(context.cacheDir, "storage-test-outside.txt").apply { writeText("kept") }

        storage.delete("../cache/storage-test-outside.txt")

        assertTrue(outside.exists())
        outside.delete()
        Unit
    }

    /** A test PDF copied to where a scanner would leave its own. */
    private fun fixture(name: String): File =
        File(incoming, name).also { copy ->
            instrumentation.context.assets.open("fixtures/$name").use { asset ->
                copy.outputStream().use { asset.copyTo(it) }
            }
        }

    private fun File.asRef() = ContentRef(toUri().toString())

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {
            "%02x".format(it)
        }
}
