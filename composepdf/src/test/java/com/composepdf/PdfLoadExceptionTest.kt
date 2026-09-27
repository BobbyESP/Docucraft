/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf

import com.composepdf.PdfLoadException.Reason
import java.io.FileNotFoundException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PdfLoadExceptionTest {

    @Test
    fun aSecurityExceptionIsAPermissionWhileReadingAndAPasswordWhileParsing() {
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
    fun aMissingFileIsNotFound() {
        assertEquals(
            Reason.NOT_FOUND,
            PdfLoadException.whileReading(FileNotFoundException("gone")).reason,
        )
    }

    @Test
    fun anythingTheRendererCannotReadIsDamaged() {
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
    fun otherReadingFailuresAreUnknown() {
        assertEquals(Reason.UNKNOWN, PdfLoadException.whileReading(IOException("disk")).reason)
    }

    @Test
    fun theOriginalFailureIsKeptAsTheCause() {
        val cause = FileNotFoundException("gone")
        assertSame(cause, PdfLoadException.whileReading(cause).cause)
    }

    @Test
    fun anAlreadyClassifiedFailureIsNotClassifiedAgain() {
        val classified = PdfLoadException(Reason.NOT_FOUND)
        assertSame(classified, PdfLoadException.whileParsing(classified))
        assertSame(classified, PdfLoadException.whileReading(classified))
    }
}
