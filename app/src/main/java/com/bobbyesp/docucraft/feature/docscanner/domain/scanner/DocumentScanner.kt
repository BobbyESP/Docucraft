/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.scanner

/**
 * Captures documents.
 *
 * The contract the rest of the app scans through, and the seam the engine is swapped at. Nothing
 * here names an engine, an Android framework type or a delivery mechanism, so an implementation is
 * free to drive a system activity, an in-app camera or a stub in a test.
 *
 * Note the shape: one suspending call, start to finish. Engines that report results out of band —
 * ML Kit hands back an IntentSender and answers through an activity result — own that plumbing
 * internally rather than spreading it across the caller.
 */
interface DocumentScanner {

    /** What this engine can do. Constant for the lifetime of the instance. */
    val capabilities: ScannerCapabilities

    /**
     * Runs a capture session and suspends until it ends.
     *
     * Never throws for cancellation or for engine failure; both come back as a [ScanOutcome].
     * Cancelling the calling coroutine should abandon the session.
     *
     * @param request What to capture and in which formats.
     */
    suspend fun scan(request: ScanRequest = ScanRequest()): ScanOutcome
}
