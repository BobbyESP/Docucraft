/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Carries "start a scan" from entry points that are not the UI — the widget arrives as an Intent,
 * so only the Activity sees it, while the state holder that runs scans is elsewhere.
 *
 * A standing request rather than a signal, because two parts of the app act on one: the shell puts
 * the catalogue back on screen, the catalogue runs the scan. As a one-shot channel only the first
 * reader learnt of it, and it waited when there was no reader at all — so restoring onto an open
 * document meant the widget did nothing, then scanned unasked when the user pressed back.
 */
class ScanRequestBus {

    private val pending = MutableStateFlow(false)

    /** Whether a scan has been asked for and nobody has taken it up yet. */
    val isPending: StateFlow<Boolean> = pending.asStateFlow()

    fun request() {
        pending.value = true
    }

    /**
     * Takes the standing request, if there is one; false when there was nothing to take, so several
     * observers can race and only one acts. Watching [isPending] without taking is how the shell
     * reacts to a request it is not the one to fulfil.
     */
    fun take(): Boolean = pending.getAndUpdate { false }
}
