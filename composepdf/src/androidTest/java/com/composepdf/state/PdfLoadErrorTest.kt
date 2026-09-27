/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.state

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.composepdf.PdfLoadException
import com.composepdf.PdfLoadException.Reason
import com.composepdf.PdfSource
import com.composepdf.PdfViewer
import com.composepdf.PdfViewerState
import com.composepdf.rememberPdfViewerState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What [PdfViewerState.error] says for each way a real document fails, as the platform throws it:
 * the viewer tells the user why, and one exception type covers both a permission and a password.
 */
@RunWith(AndroidJUnit4::class)
class PdfLoadErrorTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun anEncryptedDocumentIsPasswordProtected() {
        assertReason(
            Reason.PASSWORD_PROTECTED,
            PdfSource.Asset("fixtures/password-protected.pdf"),
        )
    }

    @Test
    fun aMissingFileIsNotFound() {
        val missing =
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "gone.pdf")
        missing.delete()
        assertReason(Reason.NOT_FOUND, PdfSource.File(missing))
    }

    @Test
    fun aFileThatIsNotAPdfIsDamaged() {
        assertReason(Reason.DAMAGED, PdfSource.Bytes("This is not a PDF.".toByteArray()))
    }

    private fun assertReason(expected: Reason, source: PdfSource) {
        lateinit var state: PdfViewerState
        rule.setContent {
            state = rememberPdfViewerState()
            PdfViewer(source = source, state = state, modifier = Modifier.fillMaxSize())
        }
        rule.waitUntil(timeoutMillis = 15_000) { state.error != null }

        val error = state.error
        assertTrue("Expected a PdfLoadException, got $error", error is PdfLoadException)
        assertEquals(expected, (error as PdfLoadException).reason)
    }
}
