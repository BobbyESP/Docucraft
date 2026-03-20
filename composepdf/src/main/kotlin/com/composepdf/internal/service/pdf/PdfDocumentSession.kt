package com.composepdf.internal.service.pdf

import android.content.Context
import com.composepdf.internal.service.cache.TileDiskCache
import com.composepdf.internal.service.remote.RemotePdfException
import com.composepdf.internal.service.remote.RemotePdfLoader
import com.composepdf.RemotePdfState
import com.composepdf.PdfSource
import com.composepdf.internal.util.longLivedContext

/**
 * Manages the lifecycle and initialization of a PDF document session.
 *
 * This class serves as a coordinator for document loading concerns, decoupling the
 * viewer's gesture and viewport logic from the complexities of resource resolution.
 *
 * Key responsibilities:
 * - **Source Resolution:** Handles both local and remote [PdfSource] variants, orchestrating
 *   downloads via [RemotePdfLoader] when necessary.
 * - **Cache Management:** Derives unique document keys and triggers [TileDiskCache]
 *   cleanup when switching between different documents to reclaim storage.
 * - **State Initialization:** Interfaces with [PdfDocumentManager] to prepare the document
 *   and produces a [DocumentResult] containing essential metadata like page dimensions.
 */
internal class PdfDocumentSession(
    context: Context,
    private val documentManager: PdfDocumentManager,
    private val tileDiskCache: TileDiskCache,
    private val remoteLoaderFactory: (Context) -> RemotePdfLoader = ::RemotePdfLoader
) {
    private val appContext = context.longLivedContext()
    private var currentDocumentKey: String = ""

    suspend fun open(
        source: PdfSource,
        onRemoteState: (RemotePdfState) -> Unit = {}
    ): DocumentResult = when (source) {
        is PdfSource.Remote -> openRemote(source, onRemoteState)
        else -> openResolved(source)
    }

    private suspend fun openRemote(
        source: PdfSource.Remote,
        onRemoteState: (RemotePdfState) -> Unit
    ): DocumentResult {
        var loadedDocument: DocumentResult? = null

        remoteLoaderFactory(appContext).load(source).collect { remoteState ->
            onRemoteState(remoteState)
            when (remoteState) {
                is RemotePdfState.Cached -> loadedDocument = openResolved(PdfSource.File(remoteState.file))
                is RemotePdfState.Error -> throw RemotePdfException(
                    remoteState.type,
                    remoteState.message,
                    remoteState.cause
                )
                else -> Unit
            }
        }

        return loadedDocument
            ?: error("Remote PDF loading finished without a cached file or an error state.")
    }

    /**
     * Opens a resolved [PdfSource], managing document-specific cache lifecycle.
     *
     * This function generates a unique cache key for the document, clears any existing disk
     * tile cache if the source has changed, and initializes the [PdfDocumentManager].
     *
     * @param source The resolved local PDF source (e.g., file, asset, or URI).
     * @return A [DocumentResult] containing the stable cache key and page metadata.
     */
    private suspend fun openResolved(source: PdfSource): DocumentResult {
        val nextDocumentKey = source.hashCode().toString(16)
        if (nextDocumentKey != currentDocumentKey && currentDocumentKey.isNotEmpty()) {
            tileDiskCache.clearForDocument(currentDocumentKey)
        }
        currentDocumentKey = nextDocumentKey

        documentManager.open(source)
        val pageSizes = documentManager.getAllPageSizes()
        return DocumentResult(
            documentKey = currentDocumentKey,
            pageSizes = pageSizes,
            pageCount = documentManager.pageCount
        )
    }
}
