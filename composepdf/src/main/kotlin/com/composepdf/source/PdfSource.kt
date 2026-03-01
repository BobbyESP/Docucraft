package com.composepdf.source

import android.net.Uri
import java.io.File
import java.io.InputStream

/**
 * Represents different sources from which a PDF document can be loaded.
 * 
 * This sealed interface provides type-safe handling of various PDF sources
 * including files, assets, byte arrays, input streams, and content URIs.
 * 
 * Example usage:
 * ```kotlin
 * // Load from file
 * val source = PdfSource.FromFile(File("/path/to/document.pdf"))
 * 
 * // Load from assets
 * val source = PdfSource.FromAsset("documents/sample.pdf")
 * 
 * // Load from URI (e.g., from file picker)
 * val source = PdfSource.FromUri(contentUri)
 * ```
 */
sealed interface PdfSource {
    
    /**
     * Load PDF from a [File] on the filesystem.
     * 
     * @property file The PDF file to load
     */
    data class FromFile(val file: File) : PdfSource
    
    /**
     * Load PDF from application assets.
     * 
     * @property assetName The asset path relative to the assets folder (e.g., "documents/sample.pdf")
     */
    data class FromAsset(val assetName: String) : PdfSource
    
    /**
     * Load PDF from a byte array.
     * 
     * Note: The byte array will be written to a temporary file for use with [android.graphics.pdf.PdfRenderer],
     * as it requires a [android.os.ParcelFileDescriptor].
     * 
     * @property bytes The PDF content as a byte array
     */
    data class FromBytes(val bytes: ByteArray) : PdfSource {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as FromBytes
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()
    }
    
    /**
     * Load PDF from an [InputStream].
     * 
     * The stream provider is a lambda that creates a new stream each time it's called.
     * This allows for retry logic and ensures the stream is fresh on each access.
     * 
     * Note: The stream content will be written to a temporary file for use with [android.graphics.pdf.PdfRenderer].
     * 
     * @property streamProvider A factory function that provides a new [InputStream] when called
     */
    data class FromStream(val streamProvider: () -> InputStream) : PdfSource
    
    /**
     * Load PDF from a content [Uri].
     * 
     * This is typically used when receiving PDFs from file pickers or content providers.
     * 
     * @property uri The content URI pointing to the PDF document
     */
    data class FromUri(val uri: Uri) : PdfSource
    
    /**
     * Load PDF from a remote URL.
     * 
     * Downloads the PDF from the specified URL, caching it locally for rendering.
     * Supports authentication via custom headers.
     * 
     * Example usage:
     * ```kotlin
     * // Public URL
     * val source = PdfSource.Remote(url = "https://example.com/document.pdf")
     * 
     * // Authenticated URL
     * val source = PdfSource.Remote(
     *     url = "https://api.example.com/documents/123.pdf",
     *     headers = mapOf("Authorization" to "Bearer token123"),
     *     cacheKey = "doc_123"
     * )
     * ```
     * 
     * @property url The HTTPS URL to download the PDF from
     * @property headers Optional HTTP headers (e.g., Authorization)
     * @property cacheKey Optional custom cache key. If null, URL hash is used
     * @property cachePolicy Cache behavior configuration
     */
    data class Remote(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val cacheKey: String? = null,
        val cachePolicy: com.composepdf.remote.CachePolicy = com.composepdf.remote.CachePolicy.Default
    ) : PdfSource
}
