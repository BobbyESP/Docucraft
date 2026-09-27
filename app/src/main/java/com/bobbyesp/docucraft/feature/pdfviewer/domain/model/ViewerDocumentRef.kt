/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.model

import kotlinx.serialization.Serializable

/**
 * Which document the viewer is showing, by identity only.
 *
 * Two kinds, because the viewer opens both: documents the catalogue knows, followed by their uuid
 * so a rename or a deletion reaches the open viewer; and documents another app handed over, which
 * have no catalogue entry and are known only by where they are.
 */
@Serializable
sealed interface ViewerDocumentRef {

    @Serializable data class Catalogued(val uuid: String) : ViewerDocumentRef

    /**
     * @property uri The `content://` (rarely `file://`) location the other app granted.
     * @property displayName What the providing app calls it; the file name, usually.
     */
    @Serializable data class External(val uri: String, val displayName: String) : ViewerDocumentRef
}
