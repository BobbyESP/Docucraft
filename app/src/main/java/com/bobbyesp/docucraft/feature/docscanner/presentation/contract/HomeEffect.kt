package com.bobbyesp.docucraft.feature.docscanner.presentation.contract

import com.bobbyesp.docucraft.core.presentation.navigation.Route

sealed interface HomeEffect {
    data class Navigate(val route: Route) : HomeEffect
}