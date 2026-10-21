/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.sharing

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.bobbyesp.docucraft.feature.docscanner.domain.sharing.DocumentSharer
import com.bobbyesp.scanner.ContentRef

/** Shares through the system chooser. */
class AndroidDocumentSharer(private val context: Context) : DocumentSharer {

    override fun share(document: ContentRef) {
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, document.value.toUri())
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

        val chooser =
            Intent.createChooser(send, null).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

        context.startActivity(chooser)
    }

    private companion object {
        const val MIME_TYPE = "application/pdf"
    }
}
