/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.bobbyesp.docucraft.feature.docscanner.data.db.DatabaseTriggers
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.repository.DocumentsRepositoryImpl
import com.bobbyesp.docucraft.feature.docscanner.data.service.DocumentOperationsService
import com.bobbyesp.docucraft.feature.docscanner.data.service.DocumentOperationsServiceImpl
import com.bobbyesp.docucraft.feature.docscanner.data.storage.DocumentLocations
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentThumbnail
import com.bobbyesp.docucraft.feature.docscanner.domain.model.NewScannedDocument
import com.bobbyesp.scanner.ContentRef
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The cache of previews against real files and the platform's renderer: whether a preview is drawn,
 * where it ends up, and that it is a picture the image loader can show.
 */
@RunWith(AndroidJUnit4::class)
class CachedDocumentThumbnailsTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val database =
        Room.inMemoryDatabaseBuilder(context, DocumentsDatabase::class.java)
            .addCallback(DatabaseTriggers.CreateOnNewDatabase)
            .build()
    private val repository =
        DocumentsRepositoryImpl(database.documentDao(), DocumentLocations(context))
    private val renderer = CountingRenderer(DocumentOperationsServiceImpl(context))
    private val thumbnails = CachedDocumentThumbnails(context, database.documentDao(), renderer)

    private val documentFile = File(context.filesDir, "scans/pdf/$Name.pdf")
    private val cache = File(context.cacheDir, "thumbnails")

    private lateinit var uuid: String

    @Before
    fun saveADocument(): Unit = runBlocking {
        cache.deleteRecursively()
        documentFile.parentFile?.mkdirs()
        instrumentation.context.assets.open("fixtures/text-and-links.pdf").use { fixture ->
            documentFile.outputStream().use { fixture.copyTo(it) }
        }
        repository.saveDocument(
            NewScannedDocument(
                filename = Name,
                location =
                    ContentRef(
                        "content://${context.packageName}.fileprovider/scanned-pdfs/$Name.pdf"
                    ),
                capturedAtEpochMillis = 1,
                sizeBytes = documentFile.length(),
                pageCount = 2,
            )
        )
        uuid = repository.observeDocuments().first().single().uuid
    }

    @After
    fun cleanUp() {
        database.close()
        documentFile.delete()
        cache.deleteRecursively()
    }

    @Test
    fun aPreviewIsDrawnFromTheDocumentTheFirstTimeItIsAskedFor() = runBlocking {
        val location = thumbnails.get(DocumentThumbnail(uuid, contentVersion = 1))

        val preview = BitmapFactory.decodeFile(checkNotNull(location).value)
        assertNotNull("The preview is not a picture", preview)
        assertTrue(preview.width > 0 && preview.height > 0)
        assertTrue("Previews belong in the cache directory", location.value.startsWith(cache.path))
    }

    // A PDF page has no background of its own unless it paints one: left as it is rendered, the
    // paper is transparent and takes the colour of whatever is behind the preview.
    @Test
    fun aPageIsDrawnOnWhitePaper() = runBlocking {
        val location = checkNotNull(thumbnails.get(DocumentThumbnail(uuid, contentVersion = 1)))

        val preview = BitmapFactory.decodeFile(location.value)
        val corner = preview.getPixel(preview.width - 1, preview.height - 1)

        assertEquals(Color.WHITE, corner)
    }

    @Test
    fun aPreviewAlreadyDrawnIsNotDrawnAgain() = runBlocking {
        val thumbnail = DocumentThumbnail(uuid, contentVersion = 1)

        val first = thumbnails.get(thumbnail)
        val second = thumbnails.get(thumbnail)

        assertEquals(first, second)
        assertEquals(1, renderer.pagesDrawn)
    }

    // A list and a shelf show the same document, and ask for its preview in the same instant.
    @Test
    fun aPreviewAskedForTwiceAtOnceIsDrawnOnce() = runBlocking {
        val thumbnail = DocumentThumbnail(uuid, contentVersion = 1)

        val locations = List(8) { async { thumbnails.get(thumbnail) } }.awaitAll()

        assertEquals(1, locations.toSet().size)
        assertEquals(1, renderer.pagesDrawn)
    }

    // The old one shows content the document no longer has, and nothing will ask for it again.
    @Test
    fun aPreviewOfNewContentReplacesTheOneOfTheOldContent() = runBlocking {
        val old = checkNotNull(thumbnails.get(DocumentThumbnail(uuid, contentVersion = 1)))

        val new = checkNotNull(thumbnails.get(DocumentThumbnail(uuid, contentVersion = 2)))

        assertFalse(File(old.value).exists())
        assertTrue(File(new.value).exists())
        assertEquals(1, cache.listFiles()?.size)
    }

    // That a preview is missing is never an error: there is just nothing to show.
    @Test
    fun aDocumentWhoseFileIsGoneHasNoPreview() = runBlocking {
        documentFile.delete()

        assertNull(thumbnails.get(DocumentThumbnail(uuid, contentVersion = 1)))
        assertEquals(emptyList<File>(), cache.listFiles().orEmpty().toList())
    }

    @Test
    fun aDocumentTheCatalogueDoesNotHaveHasNoPreview() = runBlocking {
        assertNull(thumbnails.get(DocumentThumbnail("no-such-document", contentVersion = 1)))
    }

    @Test
    fun discardingForgetsEveryPreviewOfThatDocumentAndNoOther() = runBlocking {
        thumbnails.get(DocumentThumbnail(uuid, contentVersion = 1))
        val other = File(cache, "another-document.1.webp").apply { writeText("kept") }

        thumbnails.discard(uuid)

        assertEquals(listOf(other), cache.listFiles().orEmpty().toList())
    }

    // What a screen does: it hands the loader the document's own model.
    @Test
    fun theImageLoaderShowsAPreviewGivenTheDocumentsModel() = runBlocking {
        val loader = loaderWithPreviews()

        val drawn = loader.execute(request(DocumentThumbnail(uuid, contentVersion = 1)))
        val missing = loader.execute(request(DocumentThumbnail("no-such-document", 1)))

        assertTrue("Was $drawn", drawn is SuccessResult)
        assertTrue("Was $missing", missing is ErrorResult)
    }

    private fun loaderWithPreviews(): ImageLoader =
        ImageLoader.Builder(context)
            .components { DocumentThumbnailComponent(thumbnails).register(this) }
            .build()

    private fun request(thumbnail: DocumentThumbnail): ImageRequest =
        ImageRequest.Builder(context).data(thumbnail).build()

    /** The real renderer, counting how many pages it was asked to draw. */
    private class CountingRenderer(private val renderer: DocumentOperationsService) :
        DocumentOperationsService {
        @Volatile var pagesDrawn = 0

        override fun saveDocumentPageAsImage(
            documentUri: Uri,
            outputFile: File,
            pageIndex: Int,
            format: Bitmap.CompressFormat,
            quality: Int,
        ): Boolean {
            pagesDrawn++
            return renderer.saveDocumentPageAsImage(
                documentUri,
                outputFile,
                pageIndex,
                format,
                quality,
            )
        }
    }

    private companion object {
        const val Name = "Thumbnail test"
    }
}
