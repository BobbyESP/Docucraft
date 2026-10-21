/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.sharing

import com.bobbyesp.scanner.ContentRef

/** Hands a document to whatever the user picks to receive it. */
fun interface DocumentSharer {
    fun share(document: ContentRef)
}
