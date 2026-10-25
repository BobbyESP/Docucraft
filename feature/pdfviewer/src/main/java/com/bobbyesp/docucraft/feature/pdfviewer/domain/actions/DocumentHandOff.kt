/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.domain.actions

import com.bobbyesp.scanner.ContentRef

/** Opens a document in another app of the user's choosing. */
fun interface DocumentOpener {
    fun openWith(document: ContentRef)
}

/** Hands a document to the system print framework. */
fun interface DocumentPrinter {
    fun print(document: ContentRef, jobName: String)
}

/**
 * Whether [this] can be handed to another app at all: only `content://` locations can cross a
 * process boundary. Documents the app catalogues always are; one opened from elsewhere through a
 * legacy `file://` intent is not, and re-exposing an arbitrary path through the app's own provider
 * is not something the viewer should do on a stranger's behalf.
 */
fun ContentRef.canBeHandedOff(): Boolean = value.startsWith(CONTENT_SCHEME)

private const val CONTENT_SCHEME = "content://"
