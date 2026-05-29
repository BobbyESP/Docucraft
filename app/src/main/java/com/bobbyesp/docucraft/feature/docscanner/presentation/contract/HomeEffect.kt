/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.contract

import com.bobbyesp.docucraft.core.presentation.navigation.Route

sealed interface HomeEffect {
    data class Navigate(val route: Route) : HomeEffect
}
