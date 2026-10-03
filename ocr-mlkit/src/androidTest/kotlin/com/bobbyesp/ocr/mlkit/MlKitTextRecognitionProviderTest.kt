/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.ocr.mlkit

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.documentcontent.PageContentResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real recognizer over real PDFs. It needs the model Google Play services fetches, so on a
 * device that has not got it yet these tests are skipped rather than failed: what is being checked
 * is what this module makes of ML Kit's answer, not whether a download has finished.
 */
@RunWith(AndroidJUnit4::class)
class MlKitTextRecognitionProviderTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val provider = MlKitTextRecognitionProvider(context)
    private val copies = mutableListOf<File>()

    @After
    fun cleanUp() {
        copies.forEach { it.delete() }
    }

    // The page has a text layer as well, which is how the test knows what the image says.
    @Test
    fun aPageIsReadFromItsImageWithEachWordWhereItIs() = runBlocking {
        val result = read("text-and-links.pdf", page = 1)
        assumeTrue(
            "The recognition model is not on this device",
            result !is PageContentResult.Failed,
        )

        val text = assertNotNull((result as PageContentResult.Available).text).let { result.text!! }
        val words = text.lines.flatMap { it.words }

        assertEquals(ContentOrigin.RECOGNIZED, text.origin)
        assertEquals(MlKitTextRecognitionProvider.ENGINE, text.engine)
        assertTrue("Confidence was ${text.confidence}", text.confidence!! in 0f..1f)
        assertTrue(
            "Read: ${words.joinToString(" ") { it.text }}",
            words.any { it.text.startsWith("Segunda") },
        )
        // A fraction of the page, and in the upper half of it, where the paragraph is.
        val first = words.first { it.text.startsWith("Segunda") }.bounds
        assertTrue("Bounds were $first", first.left in 0f..1f && first.right in 0f..1f)
        assertTrue("Bounds were $first", first.left < first.right && first.top < first.bottom)
        assertTrue("Bounds were $first", first.bottom < 0.5f)
    }

    @Test
    fun aDocumentThatCannotBeOpenedFailsPageByPageInsteadOfThrowing() = runBlocking {
        val missing = File(context.cacheDir, "no-such-document.pdf").toUri().toString()

        val result = provider.open(DocumentSource(missing)).use { it.page(0) }

        assertTrue(result is PageContentResult.Failed)
    }

    @Test
    fun aClosedSessionReadsNothingMore() = runBlocking {
        val session = provider.open(DocumentSource(copy("text-and-links.pdf")))
        session.close()

        assertTrue(session.page(0) is PageContentResult.Failed)
    }

    private suspend fun read(fixture: String, page: Int): PageContentResult =
        provider.open(DocumentSource(copy(fixture))).use { it.page(page) }

    /** The fixture, as a file the provider can open by its location. */
    private fun copy(fixture: String): String {
        val file = File(context.cacheDir, "ocr-test-$fixture")
        instrumentation.context.assets.open("fixtures/$fixture").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        copies += file
        return file.toUri().toString()
    }
}
