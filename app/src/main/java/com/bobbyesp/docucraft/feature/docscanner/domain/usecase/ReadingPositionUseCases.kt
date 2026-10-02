/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ReadingPosition
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentActivityRepository
import kotlinx.coroutines.flow.first

/**
 * Where to open a document: where the reader left it, if the app is remembering that. `null` opens
 * it at the start.
 */
class GetReadingPositionUseCase(
    private val settings: SettingsRepository,
    private val activity: DocumentActivityRepository,
) {
    suspend operator fun invoke(documentUuid: String): ReadingPosition? =
        if (settings.settings.first().rememberReadingPosition) {
            activity.readingPosition(documentUuid)
        } else {
            null
        }
}

/**
 * Keeps where the reader is in a document, for the next time it is opened. Does nothing while the
 * user has that turned off: a position is not kept in case they turn it back on.
 */
class RememberReadingPositionUseCase(
    private val settings: SettingsRepository,
    private val activity: DocumentActivityRepository,
) {
    suspend operator fun invoke(documentUuid: String, position: ReadingPosition) {
        if (settings.settings.first().rememberReadingPosition) {
            activity.rememberReadingPosition(documentUuid, position)
        }
    }
}

/**
 * Turns remembering where documents were left on or off.
 *
 * Turning it off forgets every position already kept. Otherwise "do not remember" would mean "stop
 * adding to what you remember", and documents would go on opening where they were left months ago.
 */
class SetReadingPositionMemoryUseCase(
    private val settings: SettingsRepository,
    private val activity: DocumentActivityRepository,
) {
    suspend operator fun invoke(enabled: Boolean) {
        // The setting first: a position written between the two would otherwise outlive them.
        settings.setRememberReadingPosition(enabled)
        if (!enabled) activity.forgetReadingPositions()
    }
}
