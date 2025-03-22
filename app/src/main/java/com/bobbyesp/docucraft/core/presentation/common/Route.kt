package com.bobbyesp.docucraft.core.presentation.common

import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {

    @Serializable
    data object DocucraftNavigator : Route {
        @Serializable data object Home : Route
    }
}
