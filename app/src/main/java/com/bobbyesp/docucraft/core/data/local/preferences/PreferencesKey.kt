package com.bobbyesp.docucraft.core.data.local.preferences

import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bobbyesp.docucraft.core.presentation.preferences.theme.DarkThemePreference.DarkThemeValue
import com.bobbyesp.docucraft.core.presentation.theme.DEFAULT_SEED_COLOR
import com.materialkolor.PaletteStyle

/**
 * A class representing different types of preference keys used in the application. Each preference
 * key has a type, a key, and a default value.
 *
 * @param T The type of the preference value.
 * @property key The key used to store the preference.
 * @property defaultValue The default value of the preference.
 */
@Immutable
data class PreferencesKey<T>(val key: Preferences.Key<T>, val defaultValue: T)

object AppSettings {
    object Core {
        val COMPLETED_ONBOARDING =
            PreferencesKey(booleanPreferencesKey("completed_onboarding"), false)
    }

    object UI {
        val MARQUEE_TEXT_ENABLED =
            PreferencesKey(booleanPreferencesKey("marquee_text_enabled"), true)
    }

    object Theming {

        val DARK_THEME_VALUE =
            PreferencesKey(
                stringPreferencesKey("dark_theme_value"),
                DarkThemeValue.FOLLOW_SYSTEM.name,
            )

        val HIGH_CONTRAST = PreferencesKey(booleanPreferencesKey("high_contrast"), false)

        val USE_DYNAMIC_COLORING = PreferencesKey(booleanPreferencesKey("dynamic_coloring"), true)

        val THEME_COLOR = PreferencesKey(intPreferencesKey("theme_color"), DEFAULT_SEED_COLOR)

        val PALETTE_STYLE =
            PreferencesKey(stringPreferencesKey("palette_style"), PaletteStyle.Vibrant.name)
    }
}
