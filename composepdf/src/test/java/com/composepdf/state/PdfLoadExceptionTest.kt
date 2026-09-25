/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.state

import com.composepdf.PdfLoadException
import com.composepdf.PdfLoadException.Reason
import java.io.FileNotFoundException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PdfLoadExceptionTest {

    @Test
    fun `a security exception is a permission while reading and a password while parsing`() {
        assertEquals(
            Reason.ACCESS_DENIED,
            PdfLoadException.whileReading(SecurityException("Permission Denial")).reason,
        )
        assertEquals(
            Reason.PASSWORD_PROTECTED,
            PdfLoadException.whileParsing(SecurityException("password required")).reason,
        )
    }

    @Test
    fun `a missing file is not found`() {
        assertEquals(
            Reason.NOT_FOUND,
            PdfLoadException.whileReading(FileNotFoundException("gone")).reason,
        )
    }

    @Test
    fun `anything the renderer cannot read is damaged`() {
        assertEquals(
            Reason.DAMAGED,
            PdfLoadException.whileParsing(IOException("not in PDF format")).reason,
        )
        assertEquals(
            Reason.DAMAGED,
            PdfLoadException.whileParsing(IllegalStateException()).reason,
        )
    }

    @Test
    fun `other reading failures are unknown`() {
        assertEquals(Reason.UNKNOWN, PdfLoadException.whileReading(IOException("disk")).reason)
    }

    @Test
    fun `the original failure is kept as the cause`() {
        val cause = FileNotFoundException("gone")
        assertSame(cause, PdfLoadException.whileReading(cause).cause)
    }

    @Test
    fun `an already classified failure is not classified again`() {
        val classified = PdfLoadException(Reason.NOT_FOUND)
        assertSame(classified, PdfLoadException.whileParsing(classified))
        assertSame(classified, PdfLoadException.whileReading(classified))
    }
}
