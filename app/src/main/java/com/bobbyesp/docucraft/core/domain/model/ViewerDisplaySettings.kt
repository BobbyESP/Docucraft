/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.domain.model

/** How the PDF viewer shows a document: the settings the user can change while reading. */
data class ViewerDisplaySettings(val fitMode: ViewerFitMode, val nightMode: Boolean) {

    companion object {
        /**
         * What a document opens with when nothing else says otherwise. Fitting the width is the
         * usual reading mode, and the rendering engine's own default (decision A2 in
         * `docs/architecture/07-pdfviewer-target-architecture.md`).
         */
        val Factory = ViewerDisplaySettings(fitMode = ViewerFitMode.WIDTH, nightMode = false)
    }
}

/**
 * The viewer settings the user chose to open every document with (decision D2).
 *
 * @property enabled Whether to use [settings] at all. Off, documents open with
 *   [ViewerDisplaySettings.Factory]; [settings] is kept, so switching back on restores the choice.
 * @property settings The chosen defaults.
 */
data class ViewerDefaults(
    val enabled: Boolean = false,
    val settings: ViewerDisplaySettings = ViewerDisplaySettings.Factory,
) {
    /** What a document opens with when this session has not seen it before. */
    val effective: ViewerDisplaySettings
        get() = if (enabled) settings else ViewerDisplaySettings.Factory
}
