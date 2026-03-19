package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import kotlinx.serialization.Serializable

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route, TopLevelRoute, NavKey {
        override val icon: ImageVector
            get() = Icons.Rounded.Home
    }

    @Serializable
    data class PdfViewer(
        val documentInfo: BasicDocument
    ) : Route, NavKey

    //    @Serializable
    //    data object Playground : Route, TopLevelRoute {
    //        override val icon: ImageVector
    //            get() = Icons.Rounded.DataObject
    //    }
}

interface TopLevelRoute {
    val icon: ImageVector
}

val TopLevelRoutes = listOf<TopLevelRoute>(Route.Home) // Route.Playground
