/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.data.settings

import com.bobbyesp.docucraft.core.domain.model.ViewerDisplaySettings
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.settings.ViewerSessionSettings
import com.bobbyesp.docucraft.feature.pdfviewer.domain.settings.sessionKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Lives as long as the process, which is exactly the session D2 asks for. A single instance, shared
 * by `MainActivity` and `PdfViewerActivity`, so a document keeps its settings whichever way it is
 * opened.
 */
class InMemoryViewerSessionSettings : ViewerSessionSettings {

    private val byDocument = MutableStateFlow<Map<String, ViewerDisplaySettings>>(emptyMap())

    override fun observe(document: ViewerDocumentRef): Flow<ViewerDisplaySettings?> {
        val key = document.sessionKey
        return byDocument.map { it[key] }.distinctUntilChanged()
    }

    override fun get(document: ViewerDocumentRef): ViewerDisplaySettings? =
        byDocument.value[document.sessionKey]

    override fun set(document: ViewerDocumentRef, settings: ViewerDisplaySettings) {
        byDocument.update { it + (document.sessionKey to settings) }
    }
}
