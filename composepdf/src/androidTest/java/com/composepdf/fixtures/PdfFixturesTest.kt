/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.fixtures

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.composepdf.PdfRenderers
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the fixtures themselves: they are written by hand-rolled code in
 * `testing/pdf-fixtures/generate_fixtures.py`, so before any test relies on them this checks that
 * the platform renderer reads each one as the manifest says it should.
 */
@RunWith(AndroidJUnit4::class)
class PdfFixturesTest {

    private val context = InstrumentationRegistry.getInstrumentation().context

    private val manifest =
        JSONObject(context.assets.open("fixtures/manifest.json").bufferedReader().readText())

    @Test
    fun everyFixtureOpensWithTheExpectedPageCount() {
        for (name in manifest.keys()) {
            val entry = manifest.getJSONObject(name)
            if (entry.has("userPassword")) continue

            open(name).use { renderer ->
                assertEquals(name, entry.getInt("pageCount"), renderer.pageCount)
                // Opening every page catches a broken page tree or content stream, which
                // pageCount alone would not.
                for (index in 0 until renderer.pageCount) renderer.openPage(index).close()
            }
        }
    }

    @Test
    fun passwordProtectedFixtureIsRefused() {
        assertThrows(SecurityException::class.java) { open("password-protected.pdf").close() }
    }

    private fun open(name: String): PdfRenderer {
        val file = File(context.cacheDir, name)
        context.assets.open("fixtures/$name").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return PdfRenderers.open(
            context,
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
        )
    }
}
