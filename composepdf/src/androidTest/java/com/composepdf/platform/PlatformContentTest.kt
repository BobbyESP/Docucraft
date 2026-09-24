/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.platform

import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.models.selection.SelectionBoundary
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the native text provider will rely on from `PdfRenderer.Page`'s content APIs, established in
 * step *a* of `docs/architecture/08-pdfviewer-migration-plan.md` and pinned here so a platform
 * change shows up as a failing test rather than as a misplaced highlight.
 *
 * Expected geometry comes from `fixtures/manifest.json`, computed by the fixture generator in the
 * viewer's convention: normalized to the displayed page, top-left origin.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
class PlatformContentTest {

    private val context = InstrumentationRegistry.getInstrumentation().context

    private val manifest =
        JSONObject(context.assets.open("fixtures/manifest.json").bufferedReader().readText())

    /** The page's text carries every word, in reading order: it is what selection indexes into. */
    @Test
    fun pageTextHoldsTheWordsInReadingOrder() {
        withPage("text-and-links.pdf", 0) { page ->
            val text = page.fullText()
            var from = 0
            for (word in expectedWords("text-and-links.pdf", 0)) {
                val at = text.indexOf(word.getString("text"), from)
                assertTrue("'${word.getString("text")}' missing or out of order", at >= 0)
                from = at + 1
            }
        }
    }

    /** How the provider tells a scanned page (`NoText`) from one with text. */
    @Test
    fun imageOnlyPagesHaveBlankText() {
        withPage("scanned-image-only.pdf", 0) { assertTrue(it.fullText().isBlank()) }
        withPage("mixed-text-and-scanned.pdf", 0) { assertTrue(it.fullText().isNotBlank()) }
        withPage("mixed-text-and-scanned.pdf", 1) { assertTrue(it.fullText().isBlank()) }
    }

    /**
     * Bounds come in points of the *displayed* page (CropBox and /Rotate applied), top-left origin,
     * so dividing by `page.width`/`height` lands in the viewer's normalized space. A link drawn
     * over two lines comes back as one rectangle enclosing both: QuadPoints are not honoured.
     */
    @Test
    fun externalLinkBoundsNormalizeToTheDisplayedPage() {
        for (name in listOf("text-and-links.pdf", "rotated-mixed-sizes.pdf")) {
            val pages = manifest.getJSONObject(name).getJSONArray("pages")
            for (index in 0 until pages.length()) {
                val expected =
                    pages.getJSONObject(index).getJSONArray("links").objects().filter {
                        it.getString("kind") == "external"
                    }
                withPage(name, index) { page ->
                    val links = page.linkContents
                    assertEquals("$name p$index", expected.size, links.size)
                    for ((link, want) in links.zip(expected)) {
                        assertEquals(want.getString("uri"), link.uri.toString())
                        assertEquals(1, link.bounds.size)
                        assertClose(
                            union(want.getJSONArray("bounds")),
                            link.bounds.single().normalized(page),
                            LINK_TOLERANCE,
                        )
                    }
                }
            }
        }
    }

    /**
     * `getTextContents()` gives no bounds, so word geometry comes from selecting each word by
     * character index. The indices are those of the page text, `\r\n` included.
     */
    @Test
    fun indexSelectionYieldsEachWordAndItsBounds() {
        withPage("text-and-links.pdf", 0) { page ->
            val text = page.fullText()
            val words = WORD.findAll(text).toList()
            val expected = expectedWords("text-and-links.pdf", 0)
            assertEquals(expected.size, words.size)
            for ((match, want) in words.zip(expected)) {
                val contents = page.select(match.range.first, match.range.last + 1)
                assertEquals(want.getString("text"), contents.joinToString("") { it.text })
                assertClose(
                    want.getJSONArray("bounds").toFloats(),
                    contents.single().bounds.single().normalized(page),
                    WORD_TOLERANCE,
                )
            }
        }
        // Rotation and CropBox: the same word, on every kind of page.
        val pages = manifest.getJSONObject("rotated-mixed-sizes.pdf").getJSONArray("pages")
        for (index in 0 until pages.length()) {
            withPage("rotated-mixed-sizes.pdf", index) { page ->
                val match = WORD.findAll(page.fullText()).first { it.value == "TARGET" }
                val contents = page.select(match.range.first, match.range.last + 1)
                assertClose(
                    pages
                        .getJSONObject(index)
                        .getJSONArray("words")
                        .getJSONObject(0)
                        .getJSONArray("bounds")
                        .toFloats(),
                    contents.single().bounds.single().normalized(page),
                    WORD_TOLERANCE,
                )
            }
        }
    }

    /**
     * A canary, not a requirement. `getGotoLinks()` reports no internal links at all: not for the
     * four forms written by the fixture generator, and not for `prueba_motor_pdf.pdf`, produced
     * independently by ReportLab, whose table of contents and "back to index" links are all
     * explicit `/Dest` arrays. Same result on the emulator and on a Pixel 9 Pro XL (API 37,
     * MediaProvider module 17).
     *
     * The viewer therefore treats internal links as unavailable. If this starts failing, the
     * platform has begun reporting them: wire them in (plan phase d) and turn this into a real
     * check.
     */
    @Test
    fun internalLinksAreNotReported_revisitIfThisFails() {
        for (name in listOf("text-and-links.pdf", "prueba_motor_pdf.pdf")) {
            open(name).use { renderer ->
                for (index in 0 until minOf(renderer.pageCount, DUMP_PAGE_LIMIT)) {
                    renderer.openPage(index).use { page ->
                        assertEquals("$name p$index", 0, page.gotoLinks.size)
                    }
                }
            }
        }
    }

    /**
     * Not a check: dumps everything the content APIs return for every fixture to
     * `files/spike-report.json` in the test app, for re-running step *a* on another platform
     * version or against a new fixture. Skipped unless asked for, keeping the APK installed:
     * ```
     * ./gradlew :composepdf:connectedDebugAndroidTest \
     *   -Pandroid.testInstrumentationRunnerArguments.class=com.composepdf.platform.PlatformContentTest#dumpPlatformContent \
     *   -Pandroid.testInstrumentationRunnerArguments.dumpPlatformContent=true \
     *   -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
     * adb shell run-as com.composepdf.test cat files/spike-report.json
     * ```
     */
    @Test
    fun dumpPlatformContent() {
        assumeTrue(
            "Diagnostic; pass dumpPlatformContent=true to run it",
            InstrumentationRegistry.getArguments().getString("dumpPlatformContent") == "true",
        )
        val report = JSONObject()
        for (name in context.assets.list("fixtures").orEmpty().filter { it.endsWith(".pdf") }) {
            if ("password" in name) continue
            val pages = JSONArray()
            open(name).use { renderer ->
                for (index in 0 until minOf(renderer.pageCount, DUMP_PAGE_LIMIT)) {
                    renderer.openPage(index).use { page ->
                        pages.put(
                            JSONObject()
                                .put("index", index)
                                .put("width", page.width)
                                .put("height", page.height)
                                .put(
                                    "texts",
                                    JSONArray(
                                        page.textContents.map {
                                            JSONObject()
                                                .put("text", it.text)
                                                .put("bounds", it.bounds.toJson())
                                        }
                                    ),
                                )
                                .put(
                                    "links",
                                    JSONArray(
                                        page.linkContents.map {
                                            JSONObject()
                                                .put("uri", it.uri.toString())
                                                .put("bounds", it.bounds.toJson())
                                        }
                                    ),
                                )
                                .put(
                                    "gotos",
                                    JSONArray(
                                        page.gotoLinks.map {
                                            JSONObject()
                                                .put("page", it.destination.pageNumber)
                                                .put("x", it.destination.xCoordinate.toDouble())
                                                .put("y", it.destination.yCoordinate.toDouble())
                                                .put("zoom", it.destination.zoom.toDouble())
                                                .put("bounds", it.bounds.toJson())
                                        }
                                    ),
                                )
                        )
                    }
                }
            }
            report.put(name, pages)
        }
        File(context.filesDir, "spike-report.json").writeText(report.toString(2))
    }

    // ---------------------------------------------------------------------------------- helpers

    private fun PdfRenderer.Page.fullText(): String = textContents.joinToString("") { it.text }

    private fun PdfRenderer.Page.select(start: Int, stop: Int) =
        selectContent(SelectionBoundary(start), SelectionBoundary(stop))
            ?.selectedTextContents
            .orEmpty()

    private fun RectF.normalized(page: PdfRenderer.Page) =
        floatArrayOf(left / page.width, top / page.height, right / page.width, bottom / page.height)

    private fun expectedWords(name: String, page: Int): List<JSONObject> =
        manifest
            .getJSONObject(name)
            .getJSONArray("pages")
            .getJSONObject(page)
            .getJSONArray("words")
            .objects()

    private fun union(rects: JSONArray): FloatArray {
        val all = (0 until rects.length()).map { rects.getJSONArray(it).toFloats() }
        return floatArrayOf(
            all.minOf { it[0] },
            all.minOf { it[1] },
            all.maxOf { it[2] },
            all.maxOf { it[3] },
        )
    }

    private fun assertClose(expected: FloatArray, actual: FloatArray, tolerance: Float) {
        val worst = expected.indices.maxOf { abs(expected[it] - actual[it]) }
        assertTrue(
            "expected ${expected.contentToString()} got ${actual.contentToString()}",
            worst <= tolerance,
        )
    }

    private fun JSONArray.toFloats() = FloatArray(length()) { getDouble(it).toFloat() }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun List<RectF>.toJson() =
        JSONArray(
            map {
                JSONArray(
                    listOf(
                        it.left.toDouble(),
                        it.top.toDouble(),
                        it.right.toDouble(),
                        it.bottom.toDouble(),
                    )
                )
            }
        )

    private fun withPage(name: String, index: Int, block: (PdfRenderer.Page) -> Unit) {
        open(name).use { renderer -> renderer.openPage(index).use(block) }
    }

    private fun open(name: String): PdfRenderer {
        val file = File(context.cacheDir, name)
        context.assets.open("fixtures/$name").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
    }

    private companion object {
        val WORD = Regex("\\S+")

        /** Enough to cover a table of contents and its targets without dumping 320 pages. */
        const val DUMP_PAGE_LIMIT = 30

        /** Link rects come rounded to whole points: about 1 pt of slack on a 595 pt page. */
        const val LINK_TOLERANCE = 0.004f

        /**
         * Words come back as glyph ink boxes; the manifest uses the font's ascent/descent box,
         * which is a little taller.
         */
        const val WORD_TOLERANCE = 0.01f
    }
}
