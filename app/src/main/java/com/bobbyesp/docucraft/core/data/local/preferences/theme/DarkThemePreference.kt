package com.bobbyesp.docucraft.core.data.local.preferences.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable

@Stable
data class DarkThemePreference(
    val darkThemeValue: DarkThemeValue = DarkThemeValue.FOLLOW_SYSTEM,
    val isHighContrastModeEnabled: Boolean = false,
) {
    enum class DarkThemeValue {
        FOLLOW_SYSTEM,
        DARK,
        LIGHT,
    }

    /**
     * Determines if the dark theme should be active based on the preference and the system state.
     */
    @Composable
    @ReadOnlyComposable
    fun isDarkTheme(): Boolean {
        return when (darkThemeValue) {
            DarkThemeValue.FOLLOW_SYSTEM -> isSystemInDarkTheme()
            DarkThemeValue.DARK -> true
            DarkThemeValue.LIGHT -> false
        }
    }
}
