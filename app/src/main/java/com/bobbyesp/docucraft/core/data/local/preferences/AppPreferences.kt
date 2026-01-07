package com.bobbyesp.docucraft.core.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.bobbyesp.docucraft.core.data.local.preferences.PreferencesKey.Companion.DARK_THEME_VALUE
import com.bobbyesp.docucraft.core.data.local.preferences.PreferencesKey.Companion.HIGH_CONTRAST
import com.bobbyesp.docucraft.core.data.local.preferences.PreferencesKey.Companion.USE_DYNAMIC_COLORING
import com.bobbyesp.docucraft.core.data.local.preferences.theme.DarkThemePreference
import com.bobbyesp.docucraft.core.presentation.theme.isDynamicColoringSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class AppPreferences(
    private val dataStore: DataStore<Preferences>,
) : AppPreferencesController {

    /**
     * Special handling for dynamic coloring which requires device support.
     */
    suspend fun updateDynamicColoring(dynamicColoring: Boolean, onCantEnable: () -> Unit) {
        if (dynamicColoring && !isDynamicColoringSupported()) {
            onCantEnable()
            saveSetting(USE_DYNAMIC_COLORING, false)
            return
        } else {
            saveSetting(USE_DYNAMIC_COLORING, dynamicColoring)
        }
    }

    /**
     * Updates the simplified Dark Theme preferences (Value + Contrast).
     * This is a composite update helper.
     */
    suspend fun updateDarkThemePreferences(darkThemePreference: DarkThemePreference) {
        saveSetting(DARK_THEME_VALUE, darkThemePreference.darkThemeValue.name)
        saveSetting(HIGH_CONTRAST, darkThemePreference.isHighContrastModeEnabled)
    }

    override suspend fun <T> saveSetting(key: PreferencesKey<T>, value: T) {
        dataStore.edit { preferences ->
            preferences[key.key] = value
        }
    }

    override fun <T> getSettingFlow(key: PreferencesKey<T>, defaultValue: T?): Flow<T> {
        return dataStore.data.map { preferences ->
            preferences[key.key] ?: defaultValue ?: key.defaultValue
        }
    }

    override suspend fun <T> getSetting(key: PreferencesKey<T>, defaultValue: T?): T {
        val preferences = dataStore.data.firstOrNull()
        return preferences?.get(key.key) ?: defaultValue ?: key.defaultValue
    }
}
