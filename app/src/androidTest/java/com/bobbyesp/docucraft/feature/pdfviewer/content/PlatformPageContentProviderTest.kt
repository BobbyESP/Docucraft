/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.content

import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.bobbyesp.docucraft.feature.pdfviewer.data.content.PlatformPageContentProvider
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.DocumentSelection
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.PageLink
import com.bobbyesp.documentcontent.TextPosition
import com.bobbyesp.documentcontent.TextSelection
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The native provider against the test PDFs, whose expected geometry the fixture generator wrote to
 * `fixtures/manifest.json` in the viewer's space: normalized to the displayed page, top-left
 * origin. What the provider returns is what the viewer will draw a highlight over, so matching the
 * manifest means matching what is on screen.
 */
@RunWith(AndroidJUnit4::class)
class PlatformPageContentProviderTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val assets = instrumentation.context.assets
    private val target = instrumentation.targetContext
    private val provider = PlatformPageContentProvider(target)

    private val manifest =
        JSONObject(assets.open("fixtures/manifest.json").bufferedReader().readText())

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun wordsComeInReadingOrderWhereThePageShowsThem() = runBlocking {
        session("text-and-links.pdf").use { session ->
            for (index in 0 until pageCount("text-and-links.pdf")) {
                val text = (session.page(index) as PageContentResult.Available).text!!
                val words = text.lines.flatMap { it.words }
                val expected = expectedWords("text-and-links.pdf", index)

                assertEquals(expected.map { it.getString("text") }, words.map { it.text })
                assertEquals(ContentOrigin.EMBEDDED, text.origin)
                for ((word, want) in words.zip(expected)) {
                    assertClose(want.getJSONArray("bounds").toRect(), word.bounds, WordTolerance)
                }
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun rotatedAndCroppedPagesPlaceWordsWhereTheyAreShown() = runBlocking {
        session("rotated-mixed-sizes.pdf").use { session ->
            for (index in 0 until pageCount("rotated-mixed-sizes.pdf")) {
                val words =
                    (session.page(index) as PageContentResult.Available).text!!.lines.flatMap {
                        it.words
                    }
                val want = expectedWords("rotated-mixed-sizes.pdf", index).single()
                val word = words.single { it.text == want.getString("text") }
                assertClose(want.getJSONArray("bounds").toRect(), word.bounds, WordTolerance)
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun externalLinksKeepTheirAddressAndPlace() = runBlocking {
        session("text-and-links.pdf").use { session ->
            val links =
                (session.page(0) as PageContentResult.Available)
                    .links
                    .filterIsInstance<PageLink.External>()
            val expected =
                manifest
                    .getJSONObject("text-and-links.pdf")
                    .getJSONArray("pages")
                    .getJSONObject(0)
                    .getJSONArray("links")
                    .objects()
                    .filter { it.getString("kind") == "external" }

            assertEquals(expected.map { it.getString("uri") }, links.map { it.uri })
            for ((link, want) in links.zip(expected)) {
                assertClose(union(want.getJSONArray("bounds")), link.bounds.single(), LinkTolerance)
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun scannedPagesHaveNoText() = runBlocking {
        session("scanned-image-only.pdf").use { session ->
            assertEquals(PageContentResult.NoText, session.page(0))
            assertEquals(PageContentResult.NoText, session.page(1))
        }
        session("mixed-text-and-scanned.pdf").use { session ->
            assertTrue(session.page(0) is PageContentResult.Available)
            assertEquals(PageContentResult.NoText, session.page(1))
            assertTrue(session.page(2) is PageContentResult.Available)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun aDocumentThatCannotBeOpenedFailsEveryPage() = runBlocking {
        session("password-protected.pdf").use { session ->
            val page = session.page(0)
            assertTrue("$page", page is PageContentResult.Failed)
        }
        provider
            .open(DocumentSource(Uri.fromFile(File(target.cacheDir, "gone.pdf")).toString()))
            .use {
                assertTrue(it.page(0) is PageContentResult.Failed)
            }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun aPageOutsideTheDocumentFails() = runBlocking {
        session("text-and-links.pdf").use { session ->
            assertTrue(session.page(99) is PageContentResult.Failed)
            assertTrue(session.page(-1) is PageContentResult.Failed)
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun aPageIsReadOnceASession() = runBlocking {
        session("text-and-links.pdf").use { session ->
            assertSame(session.page(0), session.page(0))
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun aClosedSessionReadsNothing() = runBlocking {
        val session = session("text-and-links.pdf")
        session.close()
        session.close()
        assertTrue(session.page(0) is PageContentResult.Failed)
    }

    /** D1, on any device: below API 35 nothing is opened, not even a document that is not there. */
    @Test
    fun belowApi35EveryPageIsUnsupported() = runBlocking {
        val old = PlatformPageContentProvider(target, sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        old.open(DocumentSource("content://nowhere/missing.pdf")).use {
            assertEquals(PageContentResult.Unsupported, it.page(0))
        }
    }

    /** A selection over a page break, from real pages: the words of both, in order. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun aSelectionRunsAcrossPages() = runBlocking {
        session("text-and-links.pdf").use { session ->
            val pages =
                (0..1).associateWith {
                    TextSelection((session.page(it) as PageContentResult.Available).text!!)
                }
            val lastOfFirst = pages.getValue(0).words.lastIndex
            val selection =
                DocumentSelection.between(
                    anchor = TextPosition(page = 1, word = 1),
                    focus = TextPosition(page = 0, word = lastOfFirst),
                )

            val expected =
                listOf(expectedWords("text-and-links.pdf", 0).last()) +
                    expectedWords("text-and-links.pdf", 1).take(2)
            val copied = selection.text { pages[it] }
            assertEquals(
                expected.map { it.getString("text") },
                copied.split(Regex("\\s+")),
            )
            assertTrue("pages are separated by a line break", '\n' in copied)
        }
    }

    // ---------------------------------------------------------------------------------- helpers

    private suspend fun session(name: String) = provider.open(DocumentSource(copied(name)))

    private fun copied(name: String): String {
        val file = File(target.cacheDir, name)
        assets.open("fixtures/$name").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(file).toString()
    }

    private fun pageCount(name: String) = manifest.getJSONObject(name).getInt("pageCount")

    private fun expectedWords(name: String, page: Int): List<JSONObject> =
        manifest
            .getJSONObject(name)
            .getJSONArray("pages")
            .getJSONObject(page)
            .getJSONArray("words")
            .objects()

    private fun union(rects: JSONArray): NormalizedRect =
        (0 until rects.length())
            .map { rects.getJSONArray(it).toRect() }
            .reduce(NormalizedRect::union)

    private fun assertClose(expected: NormalizedRect, actual: NormalizedRect, tolerance: Float) {
        val worst =
            maxOf(
                abs(expected.left - actual.left),
                abs(expected.top - actual.top),
                abs(expected.right - actual.right),
                abs(expected.bottom - actual.bottom),
            )
        assertTrue("expected $expected got $actual", worst <= tolerance)
    }

    private fun JSONArray.toRect() =
        NormalizedRect(
            getDouble(0).toFloat(),
            getDouble(1).toFloat(),
            getDouble(2).toFloat(),
            getDouble(3).toFloat(),
        )

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private companion object {
        /** Words are glyph ink boxes; the manifest uses the font's ascent and descent. */
        const val WordTolerance = 0.01f

        /** Link rectangles come rounded to whole points. */
        const val LinkTolerance = 0.004f
    }
}
