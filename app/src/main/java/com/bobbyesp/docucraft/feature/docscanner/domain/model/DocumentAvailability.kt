/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

/**
 * Whether a document's file could be reached the last time it was looked for. Recents shows this
 * instead of failing when the document is opened.
 */
enum class DocumentAvailability {
    AVAILABLE,

    /** Another app's file whose read permission has lapsed. */
    NO_PERMISSION,

    /** The file is not where the catalogue says. The entry stays: nothing removes it by itself. */
    NOT_FOUND,

    /** Not looked for yet. */
    UNKNOWN,
}
