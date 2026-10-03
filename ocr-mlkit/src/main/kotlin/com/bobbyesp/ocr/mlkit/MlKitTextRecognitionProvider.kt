/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.ocr.mlkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.bobbyesp.documentcontent.ContentOrigin
import com.bobbyesp.documentcontent.DocumentSource
import com.bobbyesp.documentcontent.NormalizedRect
import com.bobbyesp.documentcontent.PageContentProvider
import com.bobbyesp.documentcontent.PageContentResult
import com.bobbyesp.documentcontent.PageContentSession
import com.bobbyesp.documentcontent.PageText
import com.bobbyesp.documentcontent.TextLine
import com.bobbyesp.documentcontent.TextWord
import com.composepdf.PdfRenderers
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Reads the text of a page from its image, with ML Kit's text recognition, on the device. It is
 * what gives a camera scan words, and what reads any PDF on a device whose platform cannot read a
 * text layer.
 *
 * A page is drawn to a bitmap and recognized. Where each word is comes back in the bitmap's pixels
 * and is given as a fraction of the page, the same space the document's own text uses, so nothing
 * above this knows the two apart.
 *
 * The model is Google Play services': it is fetched once, by Play services, and a page read before
 * it has arrived fails and is read again later.
 */
class MlKitTextRecognitionProvider(context: Context) : PageContentProvider {

    private val appContext = context.applicationContext

    override val origin: ContentOrigin = ContentOrigin.RECOGNIZED

    override suspend fun open(document: DocumentSource): PageContentSession =
        RecognitionSession(
            context = appContext,
            document = Uri.parse(document.value),
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
        )

    companion object {
        /** What read the text, as it is noted with each page: ML Kit's Latin-script model. */
        const val ENGINE = "mlkit-latin"
    }
}

/**
 * The document is opened for each page and closed again, rather than held: a renderer that is kept
 * has to take turns with every other one on Android 7, which only the engine arranges, and a page
 * is recognized far more slowly than a document is opened.
 */
private class RecognitionSession(
    private val context: Context,
    private val document: Uri,
    private val recognizer: TextRecognizer,
) : PageContentSession {

    /** One page at a time: each is a bitmap of several megabytes. */
    private val mutex = Mutex()

    @Volatile private var closed = false

    override suspend fun page(index: Int): PageContentResult = mutex.withLock {
        if (closed) return PageContentResult.Failed(IllegalStateException("Session closed"))
        try {
            val bitmap = withContext(Dispatchers.IO) { render(index) }
            try {
                recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().toResult(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            PageContentResult.Failed(failure)
        }
    }

    private fun render(index: Int): Bitmap {
        val descriptor =
            context.contentResolver.openFileDescriptor(document, "r")
                ?: throw FileNotFoundException("Cannot open $document")
        return descriptor.use {
            PdfRenderers.use(context, it) { renderer ->
                renderer.openPage(index).use { page ->
                    val scale = scaleFor(page.width, page.height)
                    val bitmap =
                        Bitmap.createBitmap(
                            max(1, (page.width * scale).roundToInt()),
                            max(1, (page.height * scale).roundToInt()),
                            Bitmap.Config.ARGB_8888,
                        )
                    // Paper is white. A page drawn on a transparent bitmap is black on black to
                    // the recognizer.
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }

    override fun close() {
        closed = true
        recognizer.close()
    }

    private companion object {
        /** About 200 dpi: a page's size is in points, 72 to the inch. */
        const val TargetScale = 200f / 72f

        /** The longest side a page is drawn at. A poster at 200 dpi would not fit in memory. */
        const val MaxSide = 2400

        fun scaleFor(width: Int, height: Int): Float {
            val longest = max(width, height).coerceAtLeast(1)
            return minOf(TargetScale, MaxSide.toFloat() / longest)
        }
    }
}

/** What ML Kit found, in the words of the contract. A page with no words has no text. */
private fun Text.toResult(bitmap: Bitmap): PageContentResult {
    val width = bitmap.width.toFloat()
    val height = bitmap.height.toFloat()
    val confidences = mutableListOf<Float>()
    val languages = mutableMapOf<String, Int>()

    val lines = textBlocks.flatMap { block ->
        block.lines.mapNotNull { line ->
            val words =
                line.elements.mapNotNull { element ->
                    val box = element.boundingBox ?: return@mapNotNull null
                    if (element.text.isBlank()) return@mapNotNull null
                    confidences += element.confidence
                    TextWord(text = element.text, bounds = box.normalized(width, height))
                }
            if (words.isEmpty()) return@mapNotNull null
            line.recognizedLanguage
                .takeUnless { it.isBlank() || it == UNDETERMINED }
                ?.let { languages[it] = (languages[it] ?: 0) + words.size }
            TextLine(words)
        }
    }
    if (lines.isEmpty()) return PageContentResult.NoText

    return PageContentResult.Available(
        text =
            PageText(
                lines = lines,
                origin = ContentOrigin.RECOGNIZED,
                confidence = confidences.average().toFloat().coerceIn(0f, 1f),
                engine = MlKitTextRecognitionProvider.ENGINE,
                // The language most of the words were read in.
                language = languages.maxByOrNull { it.value }?.key,
            ),
        links = emptyList(),
    )
}

private fun Rect.normalized(width: Float, height: Float) =
    NormalizedRect(
        left = (left / width).coerceIn(0f, 1f),
        top = (top / height).coerceIn(0f, 1f),
        right = (right / width).coerceIn(0f, 1f),
        bottom = (bottom / height).coerceIn(0f, 1f),
    )

/** What ML Kit answers when it could not tell the language. */
private const val UNDETERMINED = "und"
