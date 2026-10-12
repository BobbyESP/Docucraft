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
 *
 * Each of the two has its own half of the request to take. They shared one once, and the catalogue,
 * which is listening whenever it is anywhere in the back stack, took it before the shell had seen
 * it: a scan asked for from the search ran with the search still on screen, and what it saved was
 * shown to nobody.
 */
class ScanRequestBus {

    private val pending = MutableStateFlow(false)
    private val catalogueWanted = MutableStateFlow(false)

    /** Whether a scan has been asked for and nobody has taken it up yet. */
    val isPending: StateFlow<Boolean> = pending.asStateFlow()

    /** Whether a scan has been asked for and the catalogue has not been brought forward for it. */
    val isCatalogueWanted: StateFlow<Boolean> = catalogueWanted.asStateFlow()

    fun request() {
        catalogueWanted.value = true
        pending.value = true
    }

    /**
     * Takes the standing request, if there is one; false when there was nothing to take, so several
     * observers can race and only one acts.
     */
    fun take(): Boolean = pending.getAndUpdate { false }

    /**
     * Takes the shell's half: true once per request, for whoever then puts the catalogue on screen.
     */
    fun takeCatalogueWanted(): Boolean = catalogueWanted.getAndUpdate { false }
}
