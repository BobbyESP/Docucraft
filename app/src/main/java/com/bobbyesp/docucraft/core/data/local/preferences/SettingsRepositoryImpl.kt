/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bobbyesp.docucraft.core.domain.model.FontConfig
import com.bobbyesp.docucraft.core.domain.model.PaletteStyleConfig
import com.bobbyesp.docucraft.core.domain.model.ThemeConfig
import com.bobbyesp.docucraft.core.domain.model.UserPreferences
import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.io.IOException

class SettingsRepositoryImpl(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    private object PreferencesKeys {
        val DARK_THEME_VALUE = stringPreferencesKey("dark_theme_value")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val USE_DYNAMIC_COLORING = booleanPreferencesKey("dynamic_coloring")
        val THEME_COLOR = intPreferencesKey("theme_color")
        val PALETTE_STYLE = stringPreferencesKey("palette_style")
        val FONT_CONFIG = stringPreferencesKey("font_config") // legacy key for migration
        val DISPLAY_FONT = stringPreferencesKey("display_font")
        val TITLE_FONT = stringPreferencesKey("title_font")
        val BODY_FONT = stringPreferencesKey("body_font")
        val LABEL_FONT = stringPreferencesKey("label_font")
        val MONOSPACE_FONT = stringPreferencesKey("monospace_font")
        val COMPLETED_ONBOARDING = booleanPreferencesKey("completed_onboarding")
        val MARQUEE_TEXT_ENABLED = booleanPreferencesKey("marquee_text_enabled")
    }

    override val settings: Flow<UserPreferences> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                val defaultPrefs = UserPreferences()
                val legacyFont =
                    preferences[PreferencesKeys.FONT_CONFIG]?.let {
                        try {
                            FontConfig.valueOf(it)
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                    }

                UserPreferences(
                    themeConfig =
                        preferences[PreferencesKeys.DARK_THEME_VALUE]?.let {
                            try {
                                ThemeConfig.valueOf(it)
                            } catch (_: IllegalArgumentException) {
                                // Handle old values "ON" / "OFF" if they existed
                                when (it) {
                                    "ON" -> ThemeConfig.DARK
                                    "OFF" -> ThemeConfig.LIGHT
                                    else -> ThemeConfig.FOLLOW_SYSTEM
                                }
                            }
                        } ?: defaultPrefs.themeConfig,
                    isHighContrastModeEnabled =
                        preferences[PreferencesKeys.HIGH_CONTRAST]
                            ?: defaultPrefs.isHighContrastModeEnabled,
                    useDynamicColoring =
                        preferences[PreferencesKeys.USE_DYNAMIC_COLORING]
                            ?: defaultPrefs.useDynamicColoring,
                    themeSeedColor =
                        preferences[PreferencesKeys.THEME_COLOR] ?: defaultPrefs.themeSeedColor,
                    paletteStyle =
                        preferences[PreferencesKeys.PALETTE_STYLE]?.let {
                            try {
                                PaletteStyleConfig.valueOf(it)
                            } catch (_: IllegalArgumentException) {
                                defaultPrefs.paletteStyle
                            }
                        } ?: defaultPrefs.paletteStyle,
                    displayFont =
                        preferences[PreferencesKeys.DISPLAY_FONT]?.let {
                            try {
                                FontConfig.valueOf(it)
                            } catch (_: IllegalArgumentException) {
                                null
                            }
                        } ?: legacyFont ?: defaultPrefs.displayFont,
                    titleFont =
                        preferences[PreferencesKeys.TITLE_FONT]?.let {
                            try {
                                FontConfig.valueOf(it)
                            } catch (_: IllegalArgumentException) {
                                null
                            }
                        } ?: legacyFont ?: defaultPrefs.titleFont,
                    bodyFont =
                        preferences[PreferencesKeys.BODY_FONT]?.let {
                            try {
                                FontConfig.valueOf(it)
                            } catch (_: IllegalArgumentException) {
                                null
                            }
                        } ?: legacyFont ?: defaultPrefs.bodyFont,
                    labelFont =
                        preferences[PreferencesKeys.LABEL_FONT]?.let {
                            try {
                                FontConfig.valueOf(it)
                            } catch (_: IllegalArgumentException) {
                                null
                            }
                        } ?: legacyFont ?: defaultPrefs.labelFont,
                    monospaceFont =
                        preferences[PreferencesKeys.MONOSPACE_FONT]?.let {
                            try {
                                FontConfig.valueOf(it)
                            } catch (_: IllegalArgumentException) {
                                null
                            }
                        } ?: defaultPrefs.monospaceFont,
                    completedOnboarding =
                        preferences[PreferencesKeys.COMPLETED_ONBOARDING]
                            ?: defaultPrefs.completedOnboarding,
                    marqueeTextEnabled =
                        preferences[PreferencesKeys.MARQUEE_TEXT_ENABLED]
                            ?: defaultPrefs.marqueeTextEnabled,
                )
            }

    override suspend fun updateThemeConfig(themeConfig: ThemeConfig) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_THEME_VALUE] = themeConfig.name
        }
    }

    override suspend fun updateHighContrastMode(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.HIGH_CONTRAST] = enabled }
    }

    override suspend fun updateDynamicColoring(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_DYNAMIC_COLORING] = enabled
        }
    }

    override suspend fun updateThemeSeedColor(color: Int) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.THEME_COLOR] = color }
    }

    override suspend fun updatePaletteStyle(paletteStyle: PaletteStyleConfig) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PALETTE_STYLE] = paletteStyle.name
        }
    }

    override suspend fun updateDisplayFont(fontConfig: FontConfig) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISPLAY_FONT] = fontConfig.name
        }
    }

    override suspend fun updateTitleFont(fontConfig: FontConfig) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.TITLE_FONT] = fontConfig.name }
    }

    override suspend fun updateBodyFont(fontConfig: FontConfig) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.BODY_FONT] = fontConfig.name }
    }

    override suspend fun updateLabelFont(fontConfig: FontConfig) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.LABEL_FONT] = fontConfig.name }
    }

    override suspend fun updateMonospaceFont(fontConfig: FontConfig) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MONOSPACE_FONT] = fontConfig.name
        }
    }

    override suspend fun setCompletedOnboarding(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.COMPLETED_ONBOARDING] = completed
        }
    }

    override suspend fun setMarqueeTextEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MARQUEE_TEXT_ENABLED] = enabled
        }
    }
}
