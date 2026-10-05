/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.store

import android.content.Context
import com.bobbyesp.docucraft.appModules
import com.bobbyesp.docucraft.core.domain.analytics.AnalyticsEvent
import com.bobbyesp.docucraft.core.domain.model.FontConfig
import com.bobbyesp.docucraft.core.domain.model.PaletteStyleConfig
import com.bobbyesp.docucraft.core.domain.model.ThemeConfig
import com.bobbyesp.docucraft.core.domain.model.UserPreferences
import com.bobbyesp.docucraft.core.domain.model.ViewerFitMode
import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.feature.docscanner.data.db.DocumentsDatabase
import com.bobbyesp.docucraft.feature.docscanner.data.repository.DocumentActivityRepositoryImpl
import com.bobbyesp.docucraft.feature.docscanner.data.repository.DocumentsRepositoryImpl
import com.bobbyesp.docucraft.feature.docscanner.data.repository.FoldersRepositoryImpl
import com.bobbyesp.docucraft.feature.docscanner.data.repository.TagsRepositoryImpl
import com.bobbyesp.docucraft.feature.docscanner.domain.indexing.DocumentIndexQueue
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentActivityRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.FoldersRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.TagsRepository
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/** The file of the catalogue the screenshots are taken with: never the library's own. */
internal const val CAPTURE_DATABASE = "store_captures.db"

/**
 * Starts the app's graph anew around a catalogue of its own, and returns it.
 *
 * The screenshots are taken in the app as it is installed on the device, whose library is the
 * developer's: what is on screen has to be the sample library and nothing else, and nothing of the
 * real one may be touched. So the catalogue is another file, the settings are held in memory, and
 * nothing is queued or reported. Everything else is the app's: its repositories, its use cases, its
 * storage and its ViewModels.
 *
 * Started anew rather than overridden in place: the app's graph has already handed the real
 * catalogue to whatever it built on starting, and that would stay.
 */
internal fun startCaptureGraph(context: Context): Koin {
    val app = context.applicationContext
    stopKoin()
    app.deleteDatabase(CAPTURE_DATABASE)
    return startKoin {
        androidContext(app)
        allowOverride(true)
        modules(appModules + captureModule())
    }
        .koin
}

/** Closes the sample catalogue and removes its file. */
internal fun Koin.closeCaptureGraph(context: Context) {
    get<DocumentsDatabase>().close()
    context.applicationContext.deleteDatabase(CAPTURE_DATABASE)
}

private fun captureModule() = module {
    val clock = CaptureClock()

    single<DocumentsDatabase> {
        DocumentsDatabase.builder(androidContext(), name = CAPTURE_DATABASE).build()
    }

    // The same repositories, on a clock that always tells the same time: the dates on screen are
    // part of the picture.
    single<DocumentsRepository> {
        DocumentsRepositoryImpl(documentDao = get(), locations = get(), now = clock::next)
    }
    single<DocumentActivityRepository> {
        DocumentActivityRepositoryImpl(activityDao = get(), locations = get(), now = clock::next)
    }
    single<FoldersRepository> {
        FoldersRepositoryImpl(database = get(), locations = get(), now = clock::next)
    }
    single<TagsRepository> {
        TagsRepositoryImpl(database = get(), locations = get(), now = clock::next)
    }

    single<SettingsRepository> { CaptureSettings() }
    single<AnalyticsHelper> { SilentAnalytics }
    // The sample documents are read as they are saved, so that the screenshots never wait for work
    // in the background.
    single<DocumentIndexQueue> { NoIndexQueue }
}

/**
 * A clock that starts on the same morning every time and moves on by the same step each time it is
 * asked, so that the documents are a few hours apart, in the order they were saved.
 */
internal class CaptureClock {
    private val time = AtomicLong(START)

    fun next(): Long = time.getAndAdd(STEP)

    private companion object {
        /** 2 March 2026, 09:00 UTC. */
        const val START = 1_772_442_000_000L
        const val STEP = 7L * 60 * 60 * 1000
    }
}

private object SilentAnalytics : AnalyticsHelper {
    override fun logEvent(event: AnalyticsEvent) = Unit
}

private object NoIndexQueue : DocumentIndexQueue {
    override fun enqueue(documentUuid: String) = Unit
}

/**
 * The settings of an app just installed, with the theme left to the screenshots: nothing here is
 * read from, or written to, the device's own settings.
 */
private class CaptureSettings : SettingsRepository {
    private val preferences =
        MutableStateFlow(
            UserPreferences(
                themeConfig = ThemeConfig.LIGHT,
                useDynamicColoring = false,
                // Text that slides sideways never comes to rest, and a screenshot is one frame.
                marqueeTextEnabled = false,
                reviewNewScans = false,
            )
        )

    override val settings: Flow<UserPreferences> = preferences

    private fun update(change: UserPreferences.() -> UserPreferences) {
        preferences.value = preferences.value.change()
    }

    override suspend fun updateThemeConfig(themeConfig: ThemeConfig) = update {
        copy(themeConfig = themeConfig)
    }

    override suspend fun updateHighContrastMode(enabled: Boolean) = update {
        copy(isHighContrastModeEnabled = enabled)
    }

    override suspend fun updateDynamicColoring(enabled: Boolean) = update {
        copy(useDynamicColoring = enabled)
    }

    override suspend fun updateThemeSeedColor(color: Int) = update { copy(themeSeedColor = color) }

    override suspend fun updatePaletteStyle(paletteStyle: PaletteStyleConfig) = update {
        copy(paletteStyle = paletteStyle)
    }

    override suspend fun updateDisplayFont(fontConfig: FontConfig) = update {
        copy(displayFont = fontConfig)
    }

    override suspend fun updateTitleFont(fontConfig: FontConfig) = update {
        copy(titleFont = fontConfig)
    }

    override suspend fun updateBodyFont(fontConfig: FontConfig) = update {
        copy(bodyFont = fontConfig)
    }

    override suspend fun updateLabelFont(fontConfig: FontConfig) = update {
        copy(labelFont = fontConfig)
    }

    override suspend fun updateMonospaceFont(fontConfig: FontConfig) = update {
        copy(monospaceFont = fontConfig)
    }

    override suspend fun setCompletedOnboarding(completed: Boolean) = update {
        copy(completedOnboarding = completed)
    }

    override suspend fun setMarqueeTextEnabled(enabled: Boolean) = update {
        copy(marqueeTextEnabled = enabled)
    }

    override suspend fun setViewerDefaultsEnabled(enabled: Boolean) = update {
        copy(viewerDefaults = viewerDefaults.copy(enabled = enabled))
    }

    override suspend fun updateViewerDefaultFitMode(fitMode: ViewerFitMode) = update {
        copy(
            viewerDefaults =
                viewerDefaults.copy(settings = viewerDefaults.settings.copy(fitMode = fitMode))
        )
    }

    override suspend fun setViewerDefaultNightMode(enabled: Boolean) = update {
        copy(
            viewerDefaults =
                viewerDefaults.copy(settings = viewerDefaults.settings.copy(nightMode = enabled))
        )
    }

    override suspend fun setRememberReadingPosition(enabled: Boolean) = update {
        copy(rememberReadingPosition = enabled)
    }

    override suspend fun setRecognizeTextInNewDocuments(enabled: Boolean) = update {
        copy(recognizeTextInNewDocuments = enabled)
    }

    override suspend fun setReviewNewScans(enabled: Boolean) = update {
        copy(reviewNewScans = enabled)
    }
}
