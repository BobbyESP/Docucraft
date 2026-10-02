/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.model

/** How far reading a page's text has got. Every page starts as [PENDING]. */
enum class PageTextStatus {
    /** Not read yet. */
    PENDING,

    /** It has text, the document's own or recognized. */
    EXTRACTED,

    /** An image in which text recognition found no words. */
    NO_TEXT,

    /**
     * It has no text of its own that this device can read, and the user has not turned text
     * recognition on for its document. Turning it on makes the page [PENDING] again.
     */
    OCR_DISABLED,

    /** Reading it failed as many times as it is retried. */
    FAILED,
}
