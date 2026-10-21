/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.data.content

import android.content.Context
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.models.selection.SelectionBoundary
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageContentProvider
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.PageContentSession
import com.bobbyesp.documentcontent.PageLink
import com.bobbyesp.documentcontent.PageText
import com.bobbyesp.documentcontent.TextLine
import com.bobbyesp.documentcontent.TextWord
import com.composepdf.PdfRenderers
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A PDF's own text layer and links, read through the platform's `PdfRenderer` (API 35+). Below API
 * 35 every page is [PageContentResult.Unsupported] and the document is not even opened (D1).
 *
 * It opens its own renderer rather than borrowing the viewer's: content can then be read without a
 * screen, and never competes with drawing for the engine's renderers.
 *
 * `getTextContents()` gives a page's text as one block without geometry, so each word is measured
 * by selecting it by character index (`selectContent`). That costs about 0.1 ms a word, so a page
 * is read once, off the main thread, and kept in a small cache for the session. What the platform
 * was found to report is summarized in `docs/text-and-links.md`.
 *
 * @param sdkInt The platform version, for tests of the fallback.
 */
class PlatformPageContentProvider(
    context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val cacheSize: Int = DefaultCacheSize,
) : PageContentProvider {

    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver

    override val origin: ContentOrigin = ContentOrigin.EMBEDDED

    override suspend fun open(document: DocumentSource): PageContentSession {
        if (!isSupported()) return UnsupportedSession
        return withContext(Dispatchers.IO) {
            try {
                PlatformSession(openRenderer(document), cacheSize)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FailedSession(e)
            }
        }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun isSupported(): Boolean = sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    private fun openRenderer(document: DocumentSource): PdfRenderer {
        val descriptor =
            contentResolver.openFileDescriptor(document.value.toUri(), "r")
                ?: throw FileNotFoundException("Cannot open ${document.value}")
        return try {
            PdfRenderers.open(appContext, descriptor)
        } catch (e: Exception) {
            descriptor.closeQuietly()
            throw e
        }
    }

    private companion object {
        /** The pages on screen and either side, and some of a selection running over pages. */
        const val DefaultCacheSize = 12
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private class PlatformSession(private val renderer: PdfRenderer, cacheSize: Int) :
    PageContentSession {

    /** A renderer has one page open at a time. */
    private val mutex = Mutex()

    private val cache =
        object : LinkedHashMap<Int, PageContentResult>(cacheSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<Int, PageContentResult>) =
                size > cacheSize
        }

    @Volatile private var closeRequested = false
    private var closed = false

    override suspend fun page(index: Int): PageContentResult {
        val result = mutex.withLock {
            if (closeRequested) return@withLock PageContentResult.Failed(ClosedSession())
            cache[index]
                ?: withContext(Dispatchers.IO) { read(index) }
                    .also { if (it !is PageContentResult.Failed) cache[index] = it }
        }
        closeIfRequested()
        return result
    }

    /**
     * Closes now if no page is being read, otherwise as soon as it is: [page] checks after letting
     * go of the lock, and whoever gets the lock last closes.
     */
    override fun close() {
        closeRequested = true
        closeIfRequested()
    }

    private fun closeIfRequested() {
        if (!closeRequested || !mutex.tryLock()) return
        try {
            if (!closed) {
                closed = true
                cache.clear()
                renderer.close()
            }
        } finally {
            mutex.unlock()
        }
    }

    private fun read(index: Int): PageContentResult =
        try {
            if (index !in 0 until renderer.pageCount) {
                PageContentResult.Failed(IndexOutOfBoundsException("No page $index"))
            } else {
                renderer.openPage(index).use { it.content() }
            }
        } catch (e: Exception) {
            PageContentResult.Failed(e)
        }

    private fun PdfRenderer.Page.content(): PageContentResult {
        val width = width.toFloat()
        val height = height.toFloat()
        fun RectF.normalized() =
            NormalizedRect(left / width, top / height, right / width, bottom / height)

        val links =
            linkContents.map { link ->
                PageLink.External(bounds = link.bounds.map { it.normalized() }, uri = "${link.uri}")
            } +
                // The platform reports none so far, for any form of internal link (see
                // PlatformContentTest). Where on the target page is left out: it would need that
                // page's size, and this page has to be closed to open another.
                gotoLinks.map { link ->
                    PageLink.Internal(
                        bounds = link.bounds.map { it.normalized() },
                        pageIndex = link.destination.pageNumber,
                        position = null,
                    )
                }

        val text = textContents.joinToString("") { it.text }
        val lines =
            splitPageText(text).map { spans ->
                TextLine(spans.mapNotNull { span -> word(span) { it.normalized() } })
            }
        val pageText = PageText(lines, ContentOrigin.EMBEDDED)

        return when {
            !pageText.isBlank -> PageContentResult.Available(pageText, links)
            links.isNotEmpty() -> PageContentResult.Available(text = null, links = links)
            // Blank text is how an image-only page shows, such as a camera scan.
            else -> PageContentResult.NoText
        }
    }

    /**
     * A word with a box for each of its characters, each measured by selecting it alone, so a
     * selection can start and end inside it. The word's box is their union, which costs nothing
     * more. When a character cannot be measured, the word is measured whole and its characters are
     * left to be shared out evenly. `null` if even that selects nothing.
     */
    private fun PdfRenderer.Page.word(
        span: WordSpan,
        normalize: (RectF) -> NormalizedRect,
    ): TextWord? {
        val glyphs = (span.start until span.endExclusive).map { measure(it, it + 1) }
        if (glyphs.all { it != null }) {
            val boxes = glyphs.map { normalize(it!!) }
            return TextWord(span.text, boxes.reduce(NormalizedRect::union), glyphs = boxes)
        }
        val whole = measure(span.start, span.endExclusive) ?: return null
        return TextWord(span.text, normalize(whole))
    }

    /** The ink box of the characters from [start] to [end]; `null` if nothing was selected. */
    private fun PdfRenderer.Page.measure(start: Int, end: Int): RectF? =
        selectContent(SelectionBoundary(start), SelectionBoundary(end))
            ?.selectedTextContents
            ?.flatMap { it.bounds }
            ?.reduceOrNull { union, rect -> RectF(union).apply { union(rect) } }
}

/** Below API 35 (D1). */
private object UnsupportedSession : PageContentSession {
    override suspend fun page(index: Int): PageContentResult = PageContentResult.Unsupported

    override fun close() = Unit
}

/** A document that could not be opened: every page says why. */
private class FailedSession(private val cause: Throwable) : PageContentSession {
    override suspend fun page(index: Int): PageContentResult = PageContentResult.Failed(cause)

    override fun close() = Unit
}

private class ClosedSession : IllegalStateException("The content session is closed")

private fun ParcelFileDescriptor.closeQuietly() {
    try {
        close()
    } catch (_: Exception) {}
}
