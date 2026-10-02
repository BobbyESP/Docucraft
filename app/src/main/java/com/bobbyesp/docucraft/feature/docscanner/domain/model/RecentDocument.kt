/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

/**
 * A document as Recents shows it: the document, and what is known of the last time it was used.
 *
 * @property lastOpenedAtEpochMillis `null` for a document that was saved and never opened: it is
 *   recent because it is new.
 * @property availability Whether its file could be reached the last time it was looked for. Shown
 *   on the shelf, so that a document that cannot be opened says so before it is tapped.
 */
data class RecentDocument(
    val document: Document,
    val lastOpenedAtEpochMillis: Long?,
    val availability: DocumentAvailability,
) {
    /** Not known to be out of reach. A document nobody has looked for yet is worth trying. */
    val isReachable: Boolean
        get() =
            availability == DocumentAvailability.AVAILABLE ||
                availability == DocumentAvailability.UNKNOWN
}
