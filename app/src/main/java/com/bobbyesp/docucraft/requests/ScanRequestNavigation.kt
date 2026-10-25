/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.requests

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobbyesp.docucraft.core.navigation.Navigator
import com.bobbyesp.docucraft.feature.home.domain.ScanRequestBus
import com.bobbyesp.docucraft.feature.home.navigation.Home
import org.koin.compose.koinInject

/**
 * Puts the catalogue back on screen when something outside the UI asks for a scan.
 *
 * Scanning is run by the catalogue's state holder, which exists only while the catalogue is on
 * screen — a silent dependency on where the user happened to be. Where a request has to be honored
 * is a navigation question, so it is answered here rather than by a state holder that cannot see
 * the back stack. Hosted by the shell; draws nothing, and takes only its own half of the request:
 * the scan itself is the catalogue's to take, which it may do before or after this.
 */
@Composable
fun ScanRequestNavigation(navigator: Navigator) {
    val scanRequests: ScanRequestBus = koinInject()
    val isCatalogueWanted by scanRequests.isCatalogueWanted.collectAsStateWithLifecycle()

    LaunchedEffect(isCatalogueWanted) {
        if (isCatalogueWanted && scanRequests.takeCatalogueWanted()) {
            navigator.goBackWhile { it != Home }
        }
    }
}
