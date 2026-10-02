/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

/** How a document the app keeps came to be in the catalogue. It never changes afterwards. */
enum class DocumentOrigin {
    /** Captured with the scanner. */
    SCAN,

    /** Copied from a PDF another app had. */
    IMPORT,
}
