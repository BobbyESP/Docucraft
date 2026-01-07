package com.bobbyesp.docucraft.core.data.local.preferences

import kotlinx.coroutines.flow.Flow

interface AppPreferencesController {

    suspend fun <T> saveSetting(key: PreferencesKey<T>, value: T)

    fun <T> getSettingFlow(key: PreferencesKey<T>, defaultValue: T?): Flow<T>

    suspend fun <T> getSetting(key: PreferencesKey<T>, defaultValue: T?): T
}
