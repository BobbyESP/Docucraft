/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.contract

import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument

/** One-shot effects the Home screen forwards to the app shell. */
sealed interface HomeEffect {
    data class OpenDocument(val document: BasicDocument) : HomeEffect

    data object OpenSettings : HomeEffect
}
