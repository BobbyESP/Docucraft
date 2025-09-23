package com.bobbyesp.docucraft.core.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Home
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable
    data object Home : Route, TopLevelRoute {
        override val icon: ImageVector
            get() = Icons.Rounded.Home
    }

    @Serializable
    data object Playground : Route, TopLevelRoute {
        override val icon: ImageVector
            get() = Icons.Rounded.DataObject
    }
}

interface TopLevelRoute {
    val icon: ImageVector
}

val TopLevelRoutes = listOf<TopLevelRoute>(Route.Home, Route.Playground)
