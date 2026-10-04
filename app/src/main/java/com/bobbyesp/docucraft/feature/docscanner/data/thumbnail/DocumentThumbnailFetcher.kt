/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.thumbnail

import coil.ComponentRegistry
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import com.bobbyesp.docucraft.core.data.image.ImageLoaderComponent
import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentThumbnail
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.DocumentThumbnails
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Teaches the image loader what a [DocumentThumbnail] is, so a screen shows a document's preview by
 * handing the loader the document's own model. The preview is drawn the first time it is asked for,
 * off the main thread, as part of loading the image.
 */
internal class DocumentThumbnailFetcher(
    private val thumbnail: DocumentThumbnail,
    private val thumbnails: DocumentThumbnails,
) : Fetcher {

    /** `null` when there is no preview to show, which the screen shows as a failed image. */
    override suspend fun fetch(): FetchResult? {
        val location = thumbnails.get(thumbnail) ?: return null
        return SourceResult(
            source = ImageSource(file = location.value.toPath(), fileSystem = FileSystem.SYSTEM),
            mimeType = MIME_TYPE,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val thumbnails: DocumentThumbnails) : Fetcher.Factory<DocumentThumbnail> {
        override fun create(
            data: DocumentThumbnail,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = DocumentThumbnailFetcher(data, thumbnails)
    }

    private companion object {
        const val MIME_TYPE = "image/webp"
    }
}

/** The loader keeps a decoded preview in memory under this, so each version is decoded once. */
internal class DocumentThumbnailKeyer : Keyer<DocumentThumbnail> {
    override fun key(data: DocumentThumbnail, options: Options): String =
        "document-thumbnail:${data.documentUuid}:${data.contentVersion}"
}

/** What the document catalogue adds to the app's image loader. */
class DocumentThumbnailComponent(private val thumbnails: DocumentThumbnails) :
    ImageLoaderComponent {
    override fun register(registry: ComponentRegistry.Builder) {
        registry.add(DocumentThumbnailKeyer(), DocumentThumbnail::class.java)
        registry.add(DocumentThumbnailFetcher.Factory(thumbnails), DocumentThumbnail::class.java)
    }
}
