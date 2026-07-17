/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.composepdf.internal.service.pdf

import android.content.Context
import com.composepdf.PdfSource
import com.composepdf.RemotePdfState
import com.composepdf.internal.service.remote.RemotePdfException
import com.composepdf.internal.service.remote.RemotePdfLoader
import com.composepdf.internal.util.longLivedContext

/**
 * Manages the lifecycle and initialization of a PDF document session: resolves local and remote
 * [PdfSource]s, opens the document through [PdfDocumentManager] and returns page metadata.
 */
internal class PdfDocumentSession(
    context: Context,
    private val documentManager: PdfDocumentManager,
    private val remoteLoaderFactory: (Context) -> RemotePdfLoader = ::RemotePdfLoader,
) {
    private val appContext = context.longLivedContext()
    private var currentDocumentKey: String = ""

    suspend fun open(
        source: PdfSource,
        onRemoteState: (RemotePdfState) -> Unit = {},
    ): DocumentResult =
        when (source) {
            is PdfSource.Remote -> openRemote(source, onRemoteState)
            else -> openResolved(source)
        }

    private suspend fun openRemote(
        source: PdfSource.Remote,
        onRemoteState: (RemotePdfState) -> Unit,
    ): DocumentResult {
        var loadedDocument: DocumentResult? = null

        remoteLoaderFactory(appContext).load(source).collect { remoteState ->
            onRemoteState(remoteState)
            when (remoteState) {
                is RemotePdfState.Cached ->
                    loadedDocument = openResolved(PdfSource.File(remoteState.file))

                is RemotePdfState.Error ->
                    throw RemotePdfException(
                        remoteState.type,
                        remoteState.message,
                        remoteState.cause,
                    )

                else -> Unit
            }
        }

        return loadedDocument
            ?: error("Remote PDF loading finished without a cached file or an error state.")
    }

    private suspend fun openResolved(source: PdfSource): DocumentResult {
        currentDocumentKey = source.hashCode().toString(16)
        documentManager.open(source)
        val pageSizes = documentManager.getAllPageSizes()
        return DocumentResult(
            documentKey = currentDocumentKey,
            pageSizes = pageSizes,
            pageCount = documentManager.pageCount,
        )
    }
}
