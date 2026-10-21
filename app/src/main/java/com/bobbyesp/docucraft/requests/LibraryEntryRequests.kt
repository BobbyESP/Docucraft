/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.requests

import com.bobbyesp.docucraft.feature.home.domain.ScanRequestBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** Somewhere in the library that something outside the UI asks to be taken to. */
sealed interface LibraryEntryRequest {

    /** The document [documentUuid] names, which may no longer be in the library. */
    data class OpenDocument(val documentUuid: String) : LibraryEntryRequest

    /** Searching the library. */
    data object Search : LibraryEntryRequest
}

/**
 * Carries "take me there" from entry points that are not the UI: a home-screen widget arrives as an
 * Intent, which only the Activity sees, while the back stack lives in the composition.
 *
 * A standing request rather than a signal, for the reason [ScanRequestBus] is one: the Activity
 * gets the Intent before anything is composed, and a signal sent then is heard by nobody. Only the
 * latest stands, because asking for a second place means no longer wanting the first.
 */
class LibraryEntryRequests {

    private val pendingRequest = MutableStateFlow<LibraryEntryRequest?>(null)

    /** What has been asked for and nobody has taken up yet. */
    val pending: StateFlow<LibraryEntryRequest?> = pendingRequest.asStateFlow()

    fun request(request: LibraryEntryRequest) {
        pendingRequest.value = request
    }

    /** Takes the standing request, if there is one, so it is acted on once. */
    fun take(): LibraryEntryRequest? = pendingRequest.getAndUpdate { null }
}
