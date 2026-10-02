/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

import com.bobbyesp.scanner.ContentRef

/**
 * A document of the catalogue.
 *
 * It is one of two things, and never both: a document the app keeps ([Managed]), or one that
 * belongs to another app and is only referred to ([Linked]). They are one type because Recents
 * lists them together, and two types inside it because almost everything else can only be done to
 * the first: a linked document is not organized, searched or sent to the bin.
 *
 * Identified by [uuid] alone, which never changes: not when the document is renamed, moved, or
 * saved into the library. The catalogue keeps a row id of its own, but nothing above the data layer
 * has ever needed it, so it does not travel.
 *
 * @property originalName The name the scanner or the other app gave it, without extension.
 * @property title What the user called it, if they did.
 * @property suggestedTitle A title the app proposes, from the PDF's metadata or its first page. It
 *   never replaces one the user wrote.
 * @property location Where to read the document from.
 * @property sizeBytes Size of the file, or `null` when it is not known.
 * @property pageCount Pages in the document, or `null` when they have not been counted yet.
 * @property createdAtEpochMillis When it entered the catalogue.
 */
sealed interface Document {
    val uuid: String
    val originalName: String
    val title: String?
    val suggestedTitle: String?
    val description: String?
    val location: ContentRef
    val sizeBytes: Long?
    val pageCount: Int?
    val createdAtEpochMillis: Long

    /** What the document is called on screen: the user's title, else the suggested one. */
    val name: String
        get() = title ?: suggestedTitle ?: originalName

    /**
     * A document whose file is in the app's own storage, and which the app answers for.
     *
     * @property capturedAtEpochMillis When it was scanned. `null` for an imported document.
     * @property contentUpdatedAtEpochMillis When the file's content last changed.
     * @property ocrEnabled Whether the user wants the text of its image-only pages recognized.
     * @property trashedAtEpochMillis When it went to the bin, or `null` while it is in the library.
     */
    data class Managed(
        override val uuid: String,
        override val originalName: String,
        override val title: String?,
        override val suggestedTitle: String?,
        override val description: String?,
        override val location: ContentRef,
        override val sizeBytes: Long?,
        override val pageCount: Int?,
        override val createdAtEpochMillis: Long,
        val origin: DocumentOrigin,
        val capturedAtEpochMillis: Long?,
        val contentUpdatedAtEpochMillis: Long,
        val isFavorite: Boolean,
        val ocrEnabled: Boolean,
        val trashedAtEpochMillis: Long?,
    ) : Document {
        /** Its preview, which is drawn from its content and so changes when the content does. */
        val thumbnail: DocumentThumbnail
            get() = DocumentThumbnail(uuid, contentUpdatedAtEpochMillis)
    }

    /**
     * A document that belongs to another app. Only where it is, and what could be learnt of it, are
     * kept; its file is never changed or deleted from here.
     *
     * @property hasPersistedPermission Whether the other app let the permission to read it be kept.
     *   Without it the document can only be opened while that app's grant lasts.
     */
    data class Linked(
        override val uuid: String,
        override val originalName: String,
        override val title: String?,
        override val suggestedTitle: String?,
        override val description: String?,
        override val location: ContentRef,
        override val sizeBytes: Long?,
        override val pageCount: Int?,
        override val createdAtEpochMillis: Long,
        val hasPersistedPermission: Boolean,
    ) : Document
}
