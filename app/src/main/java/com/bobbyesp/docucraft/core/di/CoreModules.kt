package com.bobbyesp.docucraft.core.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.bobbyesp.docucraft.core.data.local.preferences.SettingsRepositoryImpl
import com.bobbyesp.docucraft.core.data.local.preferences.datastore.dataStore
import com.bobbyesp.docucraft.core.domain.StringProvider
import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import com.bobbyesp.docucraft.core.domain.repository.InAppNotificationsService
import com.bobbyesp.docucraft.core.domain.usecase.NotifyUserUseCase
import com.bobbyesp.docucraft.core.presentation.common.AndroidStringProvider
import com.bobbyesp.docucraft.core.presentation.notifications.SonnerNotificationServiceImpl
import com.bobbyesp.docucraft.core.presentation.MainViewModel
import com.bobbyesp.docucraft.core.presentation.screens.preferences.appearance.AppearanceViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val commonModule = module {
    single<DataStore<Preferences>> { androidContext().dataStore }
    single<SettingsRepository> { SettingsRepositoryImpl(dataStore = get()) }

    single<ImageLoader> {
        val context = androidContext()

        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .respectCacheHeaders(false)
            .allowHardware(true)
            .bitmapFactoryMaxParallelism(
                Runtime.getRuntime().availableProcessors().coerceAtMost(4)
            )
            .crossfade(true)
            .build()
    }

    single<CoroutineScope>(qualifier = named("AppMainSupervisedScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    viewModelOf(::MainViewModel)
}

val preferencesModule = module {
    viewModelOf(::AppearanceViewModel)
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