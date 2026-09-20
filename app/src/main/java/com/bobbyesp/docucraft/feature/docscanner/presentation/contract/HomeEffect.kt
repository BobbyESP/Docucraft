/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.presentation.contract

/** One-shot effects the Home screen forwards to the app shell. */
sealed interface HomeEffect {
    data class OpenDocument(val documentUuid: String) : HomeEffect

    data object OpenSettings : HomeEffect

    data class OpenDocumentActions(val documentUuid: String) : HomeEffect
}
