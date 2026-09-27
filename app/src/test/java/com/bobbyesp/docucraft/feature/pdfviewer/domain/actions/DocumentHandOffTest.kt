/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.actions

import com.bobbyesp.scanner.ContentRef
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentHandOffTest {

    @Test
    fun `catalogue documents can leave the app`() {
        assertTrue(
            ContentRef("content://com.bobbyesp.docucraft.fileprovider/documents/Scan.pdf")
                .canBeHandedOff()
        )
    }

    @Test
    fun `documents granted by other apps can be passed on`() {
        assertTrue(ContentRef("content://media/external/downloads/37").canBeHandedOff())
    }

    /** Re-exposing an arbitrary path through the app's own provider is not the viewer's call. */
    @Test
    fun `file paths cannot`() {
        assertFalse(ContentRef("file:///sdcard/Download/a.pdf").canBeHandedOff())
        assertFalse(ContentRef("/data/user/0/com.bobbyesp.docucraft/files/a.pdf").canBeHandedOff())
    }
}
