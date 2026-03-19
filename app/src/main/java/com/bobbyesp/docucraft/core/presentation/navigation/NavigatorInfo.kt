package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.bobbyesp.docucraft.R

@Immutable
enum class NavigatorInfo(val icon: ImageVector, @param:StringRes val title: Int) {
    HOME(icon = Icons.Rounded.Home, title = R.string.home);

    companion object {
        fun fromNavKey(key: NavKey): NavigatorInfo? {
            return when (key) {
                is Route.Home -> HOME
                else -> null
            }
        }
    }
}
