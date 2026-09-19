/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.scanner

/**
 * How a capture session ended.
 *
 * Deliberately not a `Result`: walking away from the scanner is an ordinary outcome, not an error,
 * and modelling it as one is exactly how it used to reach the UI disguised as a failure.
 */
sealed interface ScanOutcome {

    /** The user finished a scan and the engine produced something. */
    data class Completed(val draft: ScanDraft) : ScanOutcome

    /** The user backed out, or the system dismissed the scanner. Nothing to report. */
    data object Cancelled : ScanOutcome

    /** The scan could not be carried out. */
    data class Failed(val error: ScanError) : ScanOutcome
}

/** Why a capture session could not be carried out. */
sealed interface ScanError {

    /** The engine is missing and cannot be obtained, e.g. no Google Play Services on the device. */
    data object EngineUnavailable : ScanError

    /** The engine needs a permission the app does not hold. */
    data object PermissionDenied : ScanError

    /** The session finished but the engine produced nothing usable. */
    data object NoOutputProduced : ScanError

    /**
     * The engine itself failed. [cause] is engine-specific and meant for logs, not for the user.
     */
    data class Engine(val cause: Throwable) : ScanError
}
