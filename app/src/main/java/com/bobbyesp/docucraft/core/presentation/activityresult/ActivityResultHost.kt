/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.activityresult

import android.app.Activity
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest

/**
 * The slice of the hosting Activity that components below the UI need in order to drive system UI.
 *
 * Activity results can only be registered by an Activity, but the things that need them — scanners,
 * pickers, payment flows — have no business knowing which Activity is on screen, or that there is
 * one at all. This narrows that dependency to two operations, so those components can be driven by
 * a stub off-device.
 */
interface ActivityResultHost {

    /** Suspends until an Activity is available, then runs [block] with it. */
    suspend fun <T> withActivity(block: suspend (Activity) -> T): T

    /**
     * Launches an [IntentSenderRequest] and suspends until its result comes back.
     *
     * Requests are served one at a time. Cancelling the caller abandons the wait, but cannot
     * dismiss whatever is already on screen.
     */
    suspend fun launchIntentSender(request: IntentSenderRequest): ActivityResult
}
