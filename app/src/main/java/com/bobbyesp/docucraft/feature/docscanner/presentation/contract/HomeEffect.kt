package com.bobbyesp.docucraft.feature.docscanner.presentation.contract

import com.bobbyesp.docucraft.core.domain.notifications.NotificationType
import com.bobbyesp.docucraft.core.presentation.navigation.Route

sealed interface HomeEffect {
    data class ShowMessage(val message: String, val type: NotificationType) : HomeEffect
    data class Navigate(val route: Route) : HomeEffect
}