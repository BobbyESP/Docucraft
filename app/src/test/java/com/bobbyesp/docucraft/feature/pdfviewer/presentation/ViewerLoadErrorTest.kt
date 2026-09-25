/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation

import com.bobbyesp.docucraft.feature.pdfviewer.presentation.components.ViewerLoadError
import com.composepdf.PdfLoadException
import com.composepdf.PdfLoadException.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerLoadErrorTest {

    @Test
    fun `each reason the engine gives has its own message`() {
        val errors = Reason.entries.map { ViewerLoadError.of(PdfLoadException(it)) }
        assertEquals(Reason.entries.size, errors.toSet().size)
        assertEquals(
            ViewerLoadError.PasswordProtected,
            ViewerLoadError.of(PdfLoadException(Reason.PASSWORD_PROTECTED)),
        )
        assertEquals(
            ViewerLoadError.AccessDenied,
            ViewerLoadError.of(PdfLoadException(Reason.ACCESS_DENIED)),
        )
    }

    @Test
    fun `an unclassified failure is unknown, not shown by name`() {
        assertEquals(ViewerLoadError.Unknown, ViewerLoadError.of(IllegalStateException("boom")))
    }

    @Test
    fun `another app is offered only when it could reach the file`() {
        assertFalse(ViewerLoadError.NotFound.worthOpeningElsewhere)
        assertFalse(ViewerLoadError.AccessDenied.worthOpeningElsewhere)
        assertTrue(ViewerLoadError.PasswordProtected.worthOpeningElsewhere)
        assertTrue(ViewerLoadError.Damaged.worthOpeningElsewhere)
    }
}
