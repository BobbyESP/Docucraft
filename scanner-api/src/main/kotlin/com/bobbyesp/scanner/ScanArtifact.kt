/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.scanner

/** Something a scanner engine produced out of a capture session. */
sealed interface ScanArtifact {

    /**
     * A single PDF holding every captured page.
     *
     * @property content Where the PDF lives.
     * @property pageCount Pages in the PDF, as reported by the engine.
     */
    data class Pdf(val content: ContentRef, val pageCount: Int) : ScanArtifact

    /**
     * The captured pages as separate images, in capture order.
     *
     * Unused today, since the current engine is configured for PDF only. It exists so that an
     * engine that hands back images instead needs no change to this contract.
     */
    data class Pages(val contents: List<ContentRef>) : ScanArtifact
}
