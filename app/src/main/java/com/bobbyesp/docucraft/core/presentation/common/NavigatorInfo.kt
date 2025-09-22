package com.bobbyesp.docucraft.core.presentation.common

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.bobbyesp.docucraft.R

@Immutable
enum class NavigatorInfo(val icon: ImageVector, @StringRes val title: Int) {
    HOME(icon = Icons.Rounded.Home, title = R.string.home);

    companion object {
        fun fromRoute(route: Route): NavigatorInfo? {
            return when (route) {
                is Route.Home -> HOME
                else -> null
            }
        }
    }
}
