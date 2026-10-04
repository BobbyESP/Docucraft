/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

import com.bobbyesp.docucraft.feature.docscanner.testDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DocumentTest {

    @Test
    fun `a document with nothing else is called by its original name`() {
        assertEquals(
            "Scan_20260919_142530",
            testDocument(originalName = "Scan_20260919_142530").name,
        )
    }

    @Test
    fun `a suggested title names it until the user writes one`() {
        val suggested = testDocument(originalName = "Scan_1", suggestedTitle = "Invoice 42")

        assertEquals("Invoice 42", suggested.name)
        assertEquals("Electricity", suggested.copy(title = "Electricity").name)
    }

    // The image loader caches a preview under this: the same content must ask for the same one,
    // and new content for another.
    @Test
    fun `its preview is named by the document and the version of its content`() {
        val document = testDocument(uuid = "doc-1", createdAtEpochMillis = 10)

        assertEquals(DocumentThumbnail("doc-1", contentVersion = 10), document.thumbnail)
        assertEquals(document.thumbnail, document.copy(title = "Renamed").thumbnail)
        assertNotEquals(
            document.thumbnail,
            document.copy(contentUpdatedAtEpochMillis = 11).thumbnail,
        )
    }
}
