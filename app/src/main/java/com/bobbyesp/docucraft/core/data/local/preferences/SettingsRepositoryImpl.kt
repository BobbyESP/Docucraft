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
import com.bobbyesp.docucraft.core.presentation.theme.DEFAULT_SEED_COLOR
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.io.IOException

class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private object PreferencesKeys {
        val DARK_THEME_VALUE = stringPreferencesKey("dark_theme_value")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val USE_DYNAMIC_COLORING = booleanPreferencesKey("dynamic_coloring")
        val THEME_COLOR = intPreferencesKey("theme_color")
        val PALETTE_STYLE = stringPreferencesKey("palette_style")
        val FONT_CONFIG = stringPreferencesKey("font_config")
        val COMPLETED_ONBOARDING = booleanPreferencesKey("completed_onboarding")
        val MARQUEE_TEXT_ENABLED = booleanPreferencesKey("marquee_text_enabled")
    }

    override val settings: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                themeConfig = preferences[PreferencesKeys.DARK_THEME_VALUE]?.let {
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
                } ?: ThemeConfig.FOLLOW_SYSTEM,
                isHighContrastModeEnabled = preferences[PreferencesKeys.HIGH_CONTRAST] ?: false,
                useDynamicColoring = preferences[PreferencesKeys.USE_DYNAMIC_COLORING] ?: true,
                themeSeedColor = preferences[PreferencesKeys.THEME_COLOR] ?: DEFAULT_SEED_COLOR,
                paletteStyle = preferences[PreferencesKeys.PALETTE_STYLE]?.let {
                    try {
                        PaletteStyleConfig.valueOf(it)
                    } catch (_: IllegalArgumentException) {
                        PaletteStyleConfig.Vibrant
                    }
                } ?: PaletteStyleConfig.Vibrant,
                fontConfig = preferences[PreferencesKeys.FONT_CONFIG]?.let {
                    try {
                        FontConfig.valueOf(it)
                    } catch (_: IllegalArgumentException) {
                        FontConfig.GoogleSansFlex
                    }
                } ?: FontConfig.GoogleSansFlex,
                completedOnboarding = preferences[PreferencesKeys.COMPLETED_ONBOARDING] ?: false,
                marqueeTextEnabled = preferences[PreferencesKeys.MARQUEE_TEXT_ENABLED] ?: true
            )
        }

    override suspend fun updateThemeConfig(themeConfig: ThemeConfig) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_THEME_VALUE] = themeConfig.name
        }
    }

    override suspend fun updateHighContrastMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIGH_CONTRAST] = enabled
        }
    }

    override suspend fun updateDynamicColoring(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_DYNAMIC_COLORING] = enabled
        }
    }

    override suspend fun updateThemeSeedColor(color: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_COLOR] = color
        }
    }

    override suspend fun updatePaletteStyle(paletteStyle: PaletteStyleConfig) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PALETTE_STYLE] = paletteStyle.name
        }
    }

    override suspend fun updateFontConfig(fontConfig: FontConfig) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FONT_CONFIG] = fontConfig.name
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
