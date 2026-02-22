package com.bobbyesp.docucraft.core.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.bobbyesp.docucraft.core.presentation.notifications.AndroidStringProvider
import com.bobbyesp.docucraft.core.data.local.preferences.AppPreferences
import com.bobbyesp.docucraft.core.data.local.preferences.datastore.dataStore
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.presentation.notifications.SonnerNotificationServiceImpl
import com.bobbyesp.docucraft.core.domain.repository.InAppNotificationsService
import com.bobbyesp.docucraft.core.domain.usecase.NotifyUserUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val commonModule = module {
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

    single<CoroutineScope>(qualifier = named("AppMainSupervisedScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}

val notificationsServiceModule = module {
    single<InAppNotificationsService> {
        SonnerNotificationServiceImpl(
            coroutineScope = get(qualifier = named("AppMainSupervisedScope")),
        )
    }

    factory<StringProvider> { AndroidStringProvider(context = androidContext()) }
    factory { NotifyUserUseCase(stringProvider = get(), inAppNotificationsService = get()) }
}