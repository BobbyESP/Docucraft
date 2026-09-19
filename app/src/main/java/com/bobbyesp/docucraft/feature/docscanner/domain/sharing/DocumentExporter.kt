/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.sharing

import com.bobbyesp.scanner.ContentRef

/**
 * How an export ended.
 *
 * Sealed rather than a `Result`, for the same reason a scan outcome is: the user choosing not to
 * save anywhere is an ordinary answer, and calling it a failure is how it ends up shown as an
 * error.
 */
sealed interface ExportOutcome {
    data class Saved(val location: ContentRef) : ExportOutcome

    data object Cancelled : ExportOutcome

    data class Failed(val cause: Throwable) : ExportOutcome
}

/** Copies a document somewhere the user chooses, outside the app's own storage. */
interface DocumentExporter {
    /**
     * @param suggestedName Name without extension to offer the user.
     * @return Where it was saved, or why it was not.
     */
    suspend fun export(document: ContentRef, suggestedName: String): ExportOutcome
}
