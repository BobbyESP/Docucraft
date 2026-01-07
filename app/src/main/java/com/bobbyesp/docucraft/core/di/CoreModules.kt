package com.bobbyesp.docucraft.core.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.bobbyesp.docucraft.core.data.local.preferences.AppPreferences
import com.bobbyesp.docucraft.core.data.local.preferences.datastore.dataStore
import com.bobbyesp.docucraft.core.presentation.common.Route
import com.bobbyesp.docucraft.core.presentation.navigation.TopLevelBackStack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appCoroutinesScope = module {
    single<CoroutineScope>(qualifier = named("AppMainSupervisedScope")) {
        CoroutineScope(SupervisorJob())
    }
}

val coreFunctionalitiesModule = module {
    single<TopLevelBackStack<Route>> { TopLevelBackStack(startKey = Route.Home) }

    single<DataStore<Preferences>> { androidContext().dataStore }
    single<AppPreferences> { AppPreferences(dataStore = get()) }

    single<ImageLoader> {
        val context = androidContext()
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.4).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(7 * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .allowHardware(true)
            .crossfade(true)
            .crossfade(300)
            .bitmapFactoryMaxParallelism(12)
            .dispatcher(Dispatchers.IO)
            .build()
    }
}
