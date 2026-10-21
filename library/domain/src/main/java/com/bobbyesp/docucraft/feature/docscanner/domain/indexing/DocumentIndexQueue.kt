/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.indexing

/**
 * Where a document waits to have the text of its pages read, so that it can be found by what it
 * says. Reading is slow and nobody is waiting for it: it happens in the background, after the
 * document is saved, and goes on if the app is closed in the middle.
 */
interface DocumentIndexQueue {
    /**
     * Asks for the pages of [documentUuid] that are still to be read to be read. A document that is
     * already waiting, or being read, is not queued twice.
     */
    fun enqueue(documentUuid: String)
}
