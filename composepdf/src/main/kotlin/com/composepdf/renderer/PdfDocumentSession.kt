package com.composepdf.renderer

import android.content.Context
import android.util.Size
import com.composepdf.cache.TileDiskCache
import com.composepdf.remote.RemotePdfException
import com.composepdf.remote.RemotePdfLoader
import com.composepdf.remote.RemotePdfState
import com.composepdf.source.PdfSource

/**
 * Coordinates document loading concerns that do not belong in the viewer controller itself.
 *
 * Responsibilities:
 * - resolve local and remote [PdfSource] variants
 * - derive a stable document cache key
 * - clear disk tiles from the previous document when the source changes
 * - open the [PdfDocumentManager] and expose a compact immutable result
 *
 * This isolates document/session lifecycle from gesture and viewport logic.
 */
internal class PdfDocumentSession(
    private val context: Context,
    private val documentManager: PdfDocumentManager,
    private val tileDiskCache: TileDiskCache,
    private val remoteLoaderFactory: (Context) -> RemotePdfLoader = ::RemotePdfLoader
) {
    private var currentDocumentKey: String = ""

    suspend fun open(
        source: PdfSource,
        onRemoteState: (RemotePdfState) -> Unit = {}
    ): LoadedPdfDocument = when (source) {
        is PdfSource.Remote -> openRemote(source, onRemoteState)
        else -> openResolved(source)
    }

    private suspend fun openRemote(
        source: PdfSource.Remote,
        onRemoteState: (RemotePdfState) -> Unit
    ): LoadedPdfDocument {
        var loadedDocument: LoadedPdfDocument? = null

        remoteLoaderFactory(context).load(source).collect { remoteState ->
            onRemoteState(remoteState)
            when (remoteState) {
                is RemotePdfState.Cached -> loadedDocument =
                    openResolved(PdfSource.File(remoteState.file))

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

    private suspend fun openResolved(source: PdfSource): LoadedPdfDocument {
        val nextDocumentKey = source.hashCode().toString(16)
        if (nextDocumentKey != currentDocumentKey && currentDocumentKey.isNotEmpty()) {
            tileDiskCache.clearForDocument(currentDocumentKey)
        }
        currentDocumentKey = nextDocumentKey

        documentManager.open(source)
        val pageSizes = documentManager.getAllPageSizes()
        return LoadedPdfDocument(
            documentKey = currentDocumentKey,
            pageSizes = pageSizes,
            pageCount = documentManager.pageCount
        )
    }
}

/** Immutable result of opening a document session. */
internal data class LoadedPdfDocument(
    val documentKey: String,
    val pageSizes: List<Size>,
    val pageCount: Int
)
