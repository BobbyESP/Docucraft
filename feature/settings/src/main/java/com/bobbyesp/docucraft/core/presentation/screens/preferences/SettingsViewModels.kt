/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.screens.preferences

import com.bobbyesp.docucraft.core.presentation.screens.preferences.appearance.AppearanceViewModel
import com.bobbyesp.docucraft.core.presentation.screens.preferences.viewer.DocumentViewerSettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** The ViewModels of the settings screens. */
val settingsViewModels = module {
    viewModelOf(::AppearanceViewModel)
    viewModelOf(::DocumentViewerSettingsViewModel)
}
