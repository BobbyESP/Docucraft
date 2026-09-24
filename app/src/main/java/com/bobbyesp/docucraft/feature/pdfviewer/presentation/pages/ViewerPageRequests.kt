/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation.pages

import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.domain.settings.sessionKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Pages asked for by *Go to page*, waiting for the viewer showing that document to scroll to them.
 *
 * *Go to page* is a destination of its own, so it cannot reach the viewer's scroll state; it leaves
 * the request here instead. Retained until the viewer takes it, rather than broadcast, which is the
 * lesson of `ScanRequestBus` in the navigation phase: a request made while nobody is listening must
 * wait, not vanish. Keyed by document, so two viewers never take each other's.
 */
class ViewerPageRequests {

    private val pending = MutableStateFlow<Map<String, Int>>(emptyMap())

    fun request(document: ViewerDocumentRef, pageIndex: Int) {
        pending.update { it + (document.sessionKey to pageIndex) }
    }

    /** The page waiting for [document], or `null`. */
    fun observe(document: ViewerDocumentRef): Flow<Int?> {
        val key = document.sessionKey
        return pending.map { it[key] }.distinctUntilChanged()
    }

    /** Takes the request for [document] once, if it is still [pageIndex]. */
    fun consume(document: ViewerDocumentRef, pageIndex: Int): Boolean {
        val key = document.sessionKey
        var taken = false
        pending.update { current ->
            if (current[key] == pageIndex) {
                taken = true
                current - key
            } else current
        }
        return taken
    }
}
