/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Carries "start a scan" from entry points that are not the UI.
 *
 * The home screen widget reaches the app as an Intent, so the Activity is the only thing that sees
 * it, while the state holder that knows whether a scan is already running is elsewhere. This hands
 * the request over without either side knowing anything about scanning.
 *
 * Requests are buffered, so one made before anyone is listening still arrives. They are not
 * replayed, and a single consumer is assumed: this is a signal, not a subscription.
 */
class ScanRequestBus {

    private val channel = Channel<Unit>(Channel.BUFFERED)

    /** Emits once per request. */
    val requests: Flow<Unit> = channel.receiveAsFlow()

    suspend fun request() {
        channel.send(Unit)
    }
}
