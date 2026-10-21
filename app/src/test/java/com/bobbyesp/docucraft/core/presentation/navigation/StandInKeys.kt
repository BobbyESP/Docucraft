/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.core.presentation.navigation

import androidx.navigation3.runtime.NavKey

/*
 * Keys named and shaped like the app's, so that these tests read as the app is used, without the
 * navigation depending on the features that own the real ones. What a destination is, an overlay or
 * a pane, is said by its entry's metadata, which the tests build themselves: nothing here depends
 * on the real keys.
 */

data object Home : NavKey

data class PdfViewer(val documentUuid: String) : NavKey

data class DocumentActions(val documentUuid: String) : NavKey

data class DeleteDocument(val documentUuid: String) : NavKey

data object Settings : NavKey

data object AppearanceSettings : NavKey
