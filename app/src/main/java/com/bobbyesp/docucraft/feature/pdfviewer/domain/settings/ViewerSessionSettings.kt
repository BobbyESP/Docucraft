/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.settings

import com.bobbyesp.docucraft.core.domain.model.ViewerDisplaySettings
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import kotlinx.coroutines.flow.Flow

/**
 * What the user changed in the viewer, per document, for as long as the app runs (decision D2).
 *
 * Deliberately not persisted: reopening the app goes back to the defaults. Per document rather than
 * per screen, because the same document can be opened several times in a session, each time in a
 * new navigation entry.
 */
interface ViewerSessionSettings {

    /** What was set for [document] in this session, or `null` if nothing was. */
    fun observe(document: ViewerDocumentRef): Flow<ViewerDisplaySettings?>

    fun get(document: ViewerDocumentRef): ViewerDisplaySettings?

    fun set(document: ViewerDocumentRef, settings: ViewerDisplaySettings)
}

/**
 * The identity a document is remembered by. The uuid for catalogued documents; the location for
 * external ones, since their display name can differ between opens of the same file.
 */
internal val ViewerDocumentRef.sessionKey: String
    get() =
        when (this) {
            is ViewerDocumentRef.Catalogued -> "catalogued:$uuid"
            is ViewerDocumentRef.External -> "external:$uri"
        }
