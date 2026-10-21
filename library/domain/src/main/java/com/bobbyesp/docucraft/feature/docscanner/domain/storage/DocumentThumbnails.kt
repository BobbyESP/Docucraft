/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.storage

import com.bobbyesp.docucraft.feature.docscanner.domain.model.DocumentThumbnail
import com.bobbyesp.scanner.ContentRef

/**
 * The previews of the documents. A cache: anything in it can be thrown away and drawn again, so
 * that one is missing is never an error.
 */
interface DocumentThumbnails {

    /**
     * Where the preview [thumbnail] names is, drawing it first if it is not there yet.
     *
     * @return `null` when it cannot be drawn: the document is gone, or its file is, or the file is
     *   not one that can be rendered.
     */
    suspend fun get(thumbnail: DocumentThumbnail): ContentRef?

    /**
     * Forgets the previews of every document that is not one of [documentUuids], and whatever older
     * versions of the app left where previews used to be kept.
     */
    suspend fun retainOnly(documentUuids: Set<String>)

    /** Forgets every preview of the document [documentUuid], whichever version. */
    suspend fun discard(documentUuid: String)
}
