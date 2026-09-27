/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.content

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.bobbyesp.docucraft.feature.pdfviewer.data.content.PlatformPageContentProvider
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.DocumentSelection
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.documentcontent.NormalizedPoint
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.PageLink
import com.bobbyesp.documentcontent.TextCaret
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

    /**
     * Every character gets its own box, inside its word's and in reading order: what lets a
     * selection start and end inside a word, where the reader sees the characters.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun everyCharacterIsMeasured() = runBlocking {
        session("text-and-links.pdf").use { session ->
            val words =
                (session.page(0) as PageContentResult.Available).text!!.lines.flatMap { it.words }
            for (word in words) {
                val glyphs = checkNotNull(word.glyphs) { "no glyphs for '${word.text}'" }
                assertEquals(word.text, word.text.length, glyphs.size)
                for (glyph in glyphs) {
                    assertTrue(
                        "'${word.text}': $glyph outside ${word.bounds}",
                        glyph.left >= word.bounds.left - Slack &&
                            glyph.right <= word.bounds.right + Slack,
                    )
                }
                val lefts = glyphs.map { it.left }
                assertEquals("'${word.text}' reads left to right", lefts.sorted(), lefts)
            }
        }
    }

    /** A finger on a character boundary is at that boundary: the real glyphs, not a share-out. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun aCaretLandsOnTheBoundaryUnderTheFinger() = runBlocking {
        session("text-and-links.pdf").use { session ->
            val text = TextSelection((session.page(0) as PageContentResult.Available).text!!)
            val word = text.words.first { it.text == "Docucraft:" }
            val glyph = word.glyphs!![4] // the second "c"
            val caret = text.caretAt(NormalizedPoint(glyph.left, (glyph.top + glyph.bottom) / 2))!!

            assertEquals("Docu", text.text.substring(caret - 4, caret))
        }
    }

    /** A selection over a page break, from inside a word to inside another, on real pages. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun aSelectionRunsAcrossPagesFromInsideWords() = runBlocking {
        session("text-and-links.pdf").use { session ->
            val pages =
                (0..1).associateWith {
                    TextSelection((session.page(it) as PageContentResult.Available).text!!)
                }
            val first = pages.getValue(0).text
            val second = pages.getValue(1).text
            val selection =
                DocumentSelection.between(
                    anchor = TextCaret(page = 1, offset = 2),
                    focus = TextCaret(page = 0, offset = first.length - 3),
                )

            assertEquals(first.takeLast(3) + "\n" + second.take(2), selection.text { pages[it] })
        }
    }

    /**
     * Not a check, a measurement: measuring every character costs one platform call each. Logs how
     * long a page takes on the densest fixture, for the plan to record.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun measuringCharactersIsAffordable() = runBlocking {
        session("prueba_motor_pdf.pdf").use { session ->
            var characters = 0
            var slowest = 0L
            val pages = 10
            val started = System.nanoTime()
            for (index in 0 until pages) {
                val pageStarted = System.nanoTime()
                val page = session.page(index) as? PageContentResult.Available ?: continue
                slowest = maxOf(slowest, (System.nanoTime() - pageStarted) / 1_000_000)
                characters +=
                    page.text?.lines?.sumOf { line -> line.words.sumOf { it.text.length } } ?: 0
            }
            val total = (System.nanoTime() - started) / 1_000_000
            Log.i(
                "PageContentCost",
                "$pages pages, $characters characters, ${total}ms total, " +
                    "${total / pages}ms a page, slowest ${slowest}ms",
            )
            assertTrue("a page took ${slowest}ms", slowest < 2_000)
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

        /** Glyph ink boxes may spill a hair past the word's. */
        const val Slack = 0.002f

        /** Link rectangles come rounded to whole points. */
        const val LinkTolerance = 0.004f
    }
}
