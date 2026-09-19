/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.scanner

/**
 * What a finished capture session produced, before anything is persisted.
 *
 * A draft is short-lived: engines typically hand back a location in their own cache, valid only for
 * as long as the permission granted with the result. Copy what you need out of it promptly.
 *
 * @property artifacts Everything the engine produced, in no particular order.
 * @property capturedAtEpochMillis When the capture finished. Supplied by the caller rather than
 *   read from the clock here, so the value is the engine's and not the mapper's.
 */
data class ScanDraft(val artifacts: List<ScanArtifact>, val capturedAtEpochMillis: Long) {

    /** The produced PDF, if the engine produced one. */
    val pdf: ScanArtifact.Pdf?
        get() = artifacts.filterIsInstance<ScanArtifact.Pdf>().firstOrNull()

    /** The produced page images, if the engine produced them. */
    val pages: ScanArtifact.Pages?
        get() = artifacts.filterIsInstance<ScanArtifact.Pages>().firstOrNull()
}
