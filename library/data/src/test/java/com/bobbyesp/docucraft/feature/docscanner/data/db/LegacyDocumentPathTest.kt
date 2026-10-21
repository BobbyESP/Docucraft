/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyDocumentPathTest {

    @Test
    fun `a provider URI becomes the path of the file under the files directory`() {
        assertEquals(
            "scans/pdf/Scan_20260919_142530.pdf",
            LegacyDocumentPath.relativePathOf(
                "content://com.bobbyesp.docucraft.fileprovider/scanned-pdfs/Scan_20260919_142530.pdf"
            ),
        )
    }

    // The debug build has another authority, and a catalogue only ever holds one of them.
    @Test
    fun `the authority does not matter`() {
        assertEquals(
            "scans/pdf/Scan_1.pdf",
            LegacyDocumentPath.relativePathOf(
                "content://com.bobbyesp.docucraft.debug.fileprovider/scanned-pdfs/Scan_1.pdf"
            ),
        )
    }

    @Test
    fun `a name with spaces is decoded`() {
        assertEquals(
            "scans/pdf/Factura luz marzo.pdf",
            LegacyDocumentPath.relativePathOf(
                "content://com.bobbyesp.docucraft.fileprovider/scanned-pdfs/Factura%20luz%20marzo.pdf"
            ),
        )
    }

    // The provider encodes a name as UTF-8 bytes, so an accented letter comes as two escapes.
    @Test
    fun `a name with accents is decoded as UTF-8`() {
        assertEquals(
            "scans/pdf/Canción año 2026.pdf",
            LegacyDocumentPath.relativePathOf(
                "content://com.bobbyesp.docucraft.fileprovider/scanned-pdfs/Canci%C3%B3n%20a%C3%B1o%202026.pdf"
            ),
        )
    }

    @Test
    fun `a percent sign that starts no escape is kept`() {
        assertEquals(
            "scans/pdf/100% done.pdf",
            LegacyDocumentPath.relativePathOf(
                "content://com.bobbyesp.docucraft.fileprovider/scanned-pdfs/100%%20done.pdf"
            ),
        )
    }

    @Test
    fun `a location that is not one of the provider's documents is not recognized`() {
        assertNull(LegacyDocumentPath.relativePathOf("/data/user/0/app/files/scans/pdf/a.pdf"))
        assertNull(LegacyDocumentPath.relativePathOf("file:///sdcard/Download/a.pdf"))
        assertNull(
            LegacyDocumentPath.relativePathOf(
                "content://com.bobbyesp.docucraft.fileprovider/thumbnails/a.webp"
            )
        )
        assertNull(
            LegacyDocumentPath.relativePathOf(
                "content://com.bobbyesp.docucraft.fileprovider/scanned-pdfs/"
            )
        )
    }

    // Every document was written as "<filename>.pdf", so the name is enough to find the file.
    @Test
    fun `an unrecognized location falls back to the name the file was saved under`() {
        assertEquals(
            "scans/pdf/Scan_1.pdf",
            LegacyDocumentPath.relativePathOf("something else", filename = "Scan_1"),
        )
    }
}
