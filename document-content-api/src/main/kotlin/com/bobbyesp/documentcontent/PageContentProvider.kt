/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.documentcontent

/**
 * Reads what is on a document's pages: text with its geometry, and links. The viewer depends on
 * this and nothing else, so the PDF's own text layer and text recognition are interchangeable, and
 * can be layered page by page.
 */
interface PageContentProvider {
    val origin: ContentOrigin

    /**
     * Opens [document] for reading. The caller closes the session. A document that cannot be read
     * does not throw here: every page of its session comes back [PageContentResult.Failed].
     */
    suspend fun open(document: DocumentSource): PageContentSession
}

/**
 * A document held open for reading. Opening one is costly, so a session lasts as long as the
 * document is on screen; providers keep their own state (a renderer, a recognizer, a cache) in it.
 */
interface PageContentSession : AutoCloseable {
    suspend fun page(index: Int): PageContentResult
}
