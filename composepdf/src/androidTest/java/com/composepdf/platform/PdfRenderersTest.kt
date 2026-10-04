/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.platform

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.composepdf.MinimalPdf
import com.composepdf.PdfRenderers
import com.composepdf.PdfSource
import com.composepdf.internal.engine.BitmapPool
import com.composepdf.internal.engine.PageRenderer
import com.composepdf.internal.service.pdf.PdfDocumentManager
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What Android 7's renderer does not survive when it is used directly, done through [PdfRenderers].
 * Either fault kills the process in native code, so these tests do what provokes it and pass by
 * being still alive at the end.
 * - **A document that cannot be opened.** A renderer that fails to open closes a document it never
 *   had when it is finalized, and the platform's PDF library, which counts its users, ends up shut
 *   down under the documents that are open or never started for the next one.
 * - **Two documents drawn at once.** Nothing keeps two threads out of the PDF library, which is not
 *   made for two, and they corrupt the font cache they share.
 */
@RunWith(AndroidJUnit4::class)
class PdfRenderersTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    // A guard that failed to open would itself be a renderer that failed to open.
    @Test
    fun theGuardDocumentIsOneThePlatformOpens() {
        val guard = File(context.cacheDir, "renderers-test-guard.pdf")
        guard.writeBytes(MinimalPdf.bytes)

        PdfRenderer(ParcelFileDescriptor.open(guard, ParcelFileDescriptor.MODE_READ_ONLY)).use {
            assertEquals(1, it.pageCount)
        }
    }

    @Test
    fun aDocumentThatOpensIsOpenedAsUsual() {
        open("text-and-links.pdf").use { renderer -> assertEquals(3, renderer.pageCount) }
    }

    // The viewer tells the two apart by the exception: a password, or a damaged file.
    @Test
    fun aDocumentThatCannotBeOpenedFailsAsThePlatformFails() {
        assertThrows(SecurityException::class.java) { open("password-protected.pdf") }
        assertThrows(IOException::class.java) { open(notAPdf()) }
    }

    @Test
    fun aDocumentThatCannotBeOpenedDoesNotBreakTheNextOne() {
        repeat(3) {
            failToOpen("password-protected.pdf")
            failToOpen(notAPdf())
            letTheFailedRenderersBeFinalized()

            assertTrue(renderFirstPage(open("text-and-links.pdf")))
        }
    }

    // The library is shut down when its last user leaves. A failed open must not be counted as
    // one leaving while a document is still open.
    @Test
    fun aDocumentThatCannotBeOpenedDoesNotBreakOneThatIsOpen() {
        val alreadyOpen = open("scanned-image-only.pdf")

        failToOpen("password-protected.pdf")
        letTheFailedRenderersBeFinalized()

        assertTrue(renderFirstPage(alreadyOpen))
    }

    // Several failures in a row, each finalized, and documents opened and closed in between.
    @Test
    fun manyFailuresLeaveThePlatformUsable() {
        repeat(5) { failToOpen("password-protected.pdf") }
        letTheFailedRenderersBeFinalized()
        assertTrue(renderFirstPage(open("text-and-links.pdf")))

        repeat(5) { failToOpen(notAPdf()) }
        letTheFailedRenderersBeFinalized()
        assertTrue(renderFirstPage(open("mixed-text-and-scanned.pdf")))
        assertTrue(renderFirstPage(open("text-and-links.pdf")))
    }

    // What Home does with its previews: one renderer each, opened, drawn and closed, several at a
    // time.
    @Test
    fun documentsUsedAtOnceDoNotBreakThePlatform() {
        val document = copyOf(TEXT_ON_EVERY_PAGE)

        val drawn = atOnce { _ ->
            var pages = 0
            repeat(ROUNDS) {
                PdfRenderers.use(context, descriptorOf(document)) { renderer ->
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { page -> draw(page) }
                        pages++
                    }
                }
            }
            pages
        }

        assertEquals(List(THREADS) { ROUNDS * PAGES }, drawn)
    }

    // What the viewer does: the engine keeps two renderers over the document and draws with both.
    @Test
    fun theEnginesRenderersDrawAtOnce() {
        val documents = PdfDocumentManager(context)
        val pages = PageRenderer(BitmapPool())
        val source = PdfSource.File(copyOf(TEXT_ON_EVERY_PAGE))

        // Opened again every round: the threads collide while the fonts of a document that has
        // just been opened are drawn for the first time.
        repeat(ROUNDS) {
            runBlocking { documents.open(source) }

            val drawn = atOnce { _ ->
                var count = 0
                runBlocking {
                    for (index in 0 until documents.pageCount) {
                        documents.withPage(index) { page ->
                            pages.renderBasePage(page, page.width, page.height).recycle()
                        }
                        count++
                    }
                }
                count
            }

            assertEquals(List(THREADS) { PAGES }, drawn)
        }
        documents.close()
    }

    /** Runs [work] on [THREADS] threads that start together, and returns what each one made. */
    private fun atOnce(work: (Int) -> Int): List<Int> {
        val start = CountDownLatch(1)
        val results = IntArray(THREADS)
        val threads =
            List(THREADS) { index ->
                thread {
                    start.await()
                    results[index] = work(index)
                }
            }
        start.countDown()
        threads.forEach { it.join() }
        return results.toList()
    }

    private fun draw(page: PdfRenderer.Page) {
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmap.recycle()
    }

    private fun open(fixture: String): PdfRenderer = open(copyOf(fixture))

    private fun open(file: File): PdfRenderer = PdfRenderers.open(context, descriptorOf(file))

    private fun descriptorOf(file: File): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    private fun failToOpen(fixture: String) = failToOpen(copyOf(fixture))

    private fun failToOpen(file: File) {
        try {
            open(file).close()
            throw AssertionError("${file.name} was expected not to open")
        } catch (_: SecurityException) {} catch (_: IOException) {}
    }

    /** Opens, draws and closes a page, which is where a broken library crashes. */
    private fun renderFirstPage(renderer: PdfRenderer): Boolean = renderer.use {
        it.openPage(0).use { page ->
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap.width > 0
        }
    }

    /**
     * The fault is in the finalizer of a renderer that failed to open, so it only shows once that
     * has run.
     */
    private fun letTheFailedRenderersBeFinalized() {
        repeat(3) {
            Runtime.getRuntime().gc()
            System.runFinalization()
            Thread.sleep(50)
        }
    }

    private fun notAPdf(): File =
        File(context.cacheDir, "renderers-test-not-a-pdf.pdf").apply {
            writeText("This is not a PDF.")
        }

    private fun copyOf(fixture: String): File =
        File(context.cacheDir, "renderers-test-$fixture").also { copy ->
            instrumentation.context.assets.open("fixtures/$fixture").use { asset ->
                copy.outputStream().use { asset.copyTo(it) }
            }
        }

    private companion object {
        // Text in the same font on each of its pages: the cache the threads fight over.
        const val TEXT_ON_EVERY_PAGE = "rotated-mixed-sizes.pdf"
        const val PAGES = 5
        const val THREADS = 2
        const val ROUNDS = 60
    }
}
