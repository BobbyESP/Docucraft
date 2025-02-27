package com.bobbyesp.docucraft.core.data.local.preferences

import com.bobbyesp.docucraft.core.data.local.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow

interface AppPreferencesController {
    val userPreferencesFlow: Flow<UserPreferences>

    suspend fun getUserPreferences(): UserPreferences

    suspend fun <T> saveSetting(key: PreferencesKey<T>, value: T)

    fun <T> getSettingFlow(key: PreferencesKey<T>, defaultValue: T?): Flow<T>

    suspend fun <T> getSetting(key: PreferencesKey<T>, defaultValue: T?): T
}
