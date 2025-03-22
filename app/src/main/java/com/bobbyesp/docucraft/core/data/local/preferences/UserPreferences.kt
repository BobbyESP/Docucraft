package com.bobbyesp.docucraft.core.data.local.preferences

import androidx.compose.runtime.Stable
import com.bobbyesp.docucraft.core.data.local.preferences.theme.DarkThemePreference
import com.materialkolor.PaletteStyle

@Stable
data class UserPreferences(
    val marqueeTextEnabled: Boolean,
    val darkThemePreference: DarkThemePreference,
    val useDynamicColoring: Boolean,
    val themeColor: Int,
    val paletteStyle: PaletteStyle,
) {
    companion object {
        fun emptyUserPreferences(): UserPreferences =
            UserPreferences(
                marqueeTextEnabled = PreferencesKey.MARQUEE_TEXT_ENABLED.defaultValue,
                darkThemePreference = DarkThemePreference(),
                useDynamicColoring = PreferencesKey.USE_DYNAMIC_COLORING.defaultValue,
                themeColor = PreferencesKey.THEME_COLOR.defaultValue,
                paletteStyle = PaletteStyle.valueOf(PreferencesKey.PALETTE_STYLE.defaultValue),
            )
    }
}
