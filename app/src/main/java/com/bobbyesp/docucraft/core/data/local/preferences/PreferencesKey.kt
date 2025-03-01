package com.bobbyesp.docucraft.core.data.local.preferences

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bobbyesp.docucraft.core.data.local.preferences.theme.DarkThemePreference.Companion.DarkThemeValue
import com.bobbyesp.docucraft.core.presentation.theme.DEFAULT_SEED_COLOR
import com.materialkolor.PaletteStyle

/**
 * A sealed class representing different types of preference keys used in the application.
 * Each preference key has a type, a key, and a default value.
 *
 * @param T The type of the preference value.
 * @property key The key used to store the preference.
 * @property defaultValue The default value of the preference.
 */
sealed class PreferencesKey<T>(val key: Preferences.Key<T>, val defaultValue: T) {
    // --> Core
    data object COMPLETED_ONBOARDING :
        PreferencesKey<Boolean>(booleanPreferencesKey("completed_onboarding"), false)

    // --> UI
    data object MARQUEE_TEXT_ENABLED :
        PreferencesKey<Boolean>(booleanPreferencesKey("marquee_text_enabled"), true)

    // --> Theming
    data object DARK_THEME_VALUE :
        PreferencesKey<String>(
            stringPreferencesKey("dark_theme_value"),
            DarkThemeValue.FOLLOW_SYSTEM.name,
        )

    data object HIGH_CONTRAST :
        PreferencesKey<Boolean>(booleanPreferencesKey("high_contrast"), false)

    data object USE_DYNAMIC_COLORING :
        PreferencesKey<Boolean>(booleanPreferencesKey("dynamic_coloring"), true)

    data object THEME_COLOR :
        PreferencesKey<Int>(intPreferencesKey("theme_color"), DEFAULT_SEED_COLOR)

    data object PALETTE_STYLE :
        PreferencesKey<String>(stringPreferencesKey("palette_style"), PaletteStyle.Vibrant.name)
}
