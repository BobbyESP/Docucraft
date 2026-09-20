/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.core.presentation.navigation.Navigator
import com.bobbyesp.docucraft.feature.docscanner.domain.ScanRequestBus
import org.koin.compose.koinInject

/**
 * Puts the catalogue back on screen when something outside the UI asks for a scan.
 *
 * Scanning is run by the catalogue's state holder, which exists only while the catalogue is on
 * screen. That was a silent dependency on where the user happened to be: restore the app onto an
 * open document, press the widget, and nothing happened — the request waited for a reader that was
 * not composed, and then started the scanner unasked whenever the user next pressed back.
 *
 * Where a request has to be honoured is a navigation question, so it is answered here rather than
 * by the state holder that cannot see the back stack. Rendered by the shell, which is always in
 * composition; it draws nothing.
 *
 * The request is left standing rather than consumed: the catalogue takes it once it arrives.
 */
@Composable
fun ScanRequestNavigation(navigator: Navigator) {
    val scanRequests: ScanRequestBus = koinInject()
    val isPending by scanRequests.isPending.collectAsStateWithLifecycle()

    LaunchedEffect(isPending) {
        // Overlays included: a scan cannot start underneath a sheet asking about some other
        // document.
        if (isPending) navigator.goBackWhile { it != Home }
    }
}
