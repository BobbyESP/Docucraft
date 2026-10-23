/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.settings.di

import com.bobbyesp.docucraft.feature.settings.presentation.appearance.AppearanceViewModel
import com.bobbyesp.docucraft.feature.settings.presentation.viewer.DocumentViewerSettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The ViewModels of the settings screens. */
val settingsViewModels = module {
    viewModelOf(::AppearanceViewModel)
    viewModelOf(::DocumentViewerSettingsViewModel)
}
