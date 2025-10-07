package com.bobbyesp.docucraft.core.data.local.preferences.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.res.stringResource
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.data.local.preferences.PreferencesKey

// TODO: Rewrite the theming system

@Stable
data class DarkThemePreference(
    val darkThemeValue: DarkThemeValue =
        DarkThemeValue.valueOf(PreferencesKey.DARK_THEME_VALUE.defaultValue),
    val isHighContrastModeEnabled: Boolean = PreferencesKey.HIGH_CONTRAST.defaultValue,
) {
    companion object {
        enum class DarkThemeValue {
            FOLLOW_SYSTEM,
            ON,
            OFF,
        }
    }

    @Composable
    fun isDarkTheme(): Boolean {
        return if (darkThemeValue == DarkThemeValue.FOLLOW_SYSTEM) isSystemInDarkTheme()
        else darkThemeValue == DarkThemeValue.ON
    }

    @Composable
    fun getDarkThemeDescription(): String {
        return when (darkThemeValue) {
            DarkThemeValue.FOLLOW_SYSTEM -> stringResource(R.string.follow_system)
            DarkThemeValue.ON -> stringResource(R.string.on)
            else -> stringResource(R.string.off)
        }
    }
}
