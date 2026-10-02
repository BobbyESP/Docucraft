/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScannedDocument
import com.bobbyesp.scanner.ContentRef
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Search against a real database. What a query matches is decided by the device's SQLite and how it
 * was compiled, which a JVM test cannot stand in for.
 */
@RunWith(AndroidJUnit4::class)
class LocalDocumentsRepositoryImplTest {

    private val database =
        Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                DocumentsDatabase::class.java,
            )
            .build()
    private val repository = LocalDocumentsRepositoryImpl(database.scannedDocumentDao())

    @After
    fun closeDatabase() {
        database.close()
    }

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

    private suspend fun save(filename: String, capturedAt: Long) {
        repository.saveDocument(
            NewScannedDocument(
                filename = filename,
                location = ContentRef("content://test/$filename.pdf"),
                capturedAtEpochMillis = capturedAt,
                sizeBytes = 1,
                pageCount = 1,
                thumbnail = null,
            )
        )
    }

    private suspend fun search(query: String): List<String> =
        repository.searchDocuments(query).map { it.filename }
}
