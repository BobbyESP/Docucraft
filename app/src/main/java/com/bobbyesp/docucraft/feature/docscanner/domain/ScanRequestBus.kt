/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Carries "start a scan" from entry points that are not the UI.
 *
 * The home screen widget reaches the app as an Intent, so the Activity is the only thing that sees
 * it, while the state holder that knows whether a scan is already running is elsewhere. This hands
 * the request over without either side knowing anything about scanning.
 *
 * A standing request rather than a signal, because two different parts of the app have to act on
 * one: the shell has to put the catalogue back on screen, and the catalogue has to run the scan. As
 * a one-shot channel only whoever read it first would learn of it — and worse, the request simply
 * waited when the reader did not exist. Restoring onto an open document meant the widget did
 * nothing at all, and then launched the scanner unasked, later, the moment the user pressed back.
 *
 * The request stands until someone [take]s it, and exactly one caller can.
 */
class ScanRequestBus {

    private val pending = MutableStateFlow(false)

    /** Whether a scan has been asked for and nobody has taken it up yet. */
    val isPending: StateFlow<Boolean> = pending.asStateFlow()

    fun request() {
        pending.value = true
    }

    /**
     * Takes the standing request, if there is one.
     *
     * Returns false when there was nothing to take, so several observers can race for it and only
     * one will act. Observing [isPending] without taking is how the shell reacts to a request it is
     * not the one to fulfil.
     */
    fun take(): Boolean = pending.getAndUpdate { false }
}
