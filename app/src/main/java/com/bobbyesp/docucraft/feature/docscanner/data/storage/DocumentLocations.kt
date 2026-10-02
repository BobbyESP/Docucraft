/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.storage

import android.content.ContentResolver
import android.content.Context
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.bobbyesp.docucraft.App
import com.bobbyesp.docucraft.feature.docscanner.data.db.LegacyDocumentPath
import com.bobbyesp.scanner.ContentRef
import java.io.File

/**
 * Translates between where the catalogue says a document is and what the rest of the app opens.
 *
 * The catalogue keeps a path relative to the files directory, which does not depend on the
 * provider's authority, on the user profile or on the device. The viewer and other apps need a
 * `FileProvider` URI, which is built here whenever it is asked for and never stored.
 */
class DocumentLocations(private val context: Context) {

    /** The URI to open the document kept at [filePath], relative to the files directory. */
    fun locationOf(filePath: String): ContentRef {
        val file = File(context.filesDir, filePath)
        val uri =
            try {
                FileProvider.getUriForFile(context, App.getAuthority(context), file)
            } catch (_: IllegalArgumentException) {
                // A path the provider does not serve: the catalogue entry of a file that is not
                // there. It still has to be listed, and opening it then says the file is missing.
                file.toUri()
            }
        return ContentRef(uri.toString())
    }

    /**
     * The path the catalogue keeps for the document at [location], or `null` if it is not ours. It
     * undoes [locationOf], whichever of its two forms the location has.
     */
    fun filePathOf(location: ContentRef): String? {
        LegacyDocumentPath.relativePathOf(location.value)?.let {
            return it
        }
        val uri = location.value.toUri()
        if (uri.scheme != ContentResolver.SCHEME_FILE) return null
        return uri.path?.let(::File)?.relativeToOrNull(context.filesDir)?.invariantSeparatorsPath
    }

    /**
     * The preview of the document saved as [originalName], if one was rendered. Previews are named
     * after the document.
     */
    fun previewOf(originalName: String): ContentRef? {
        val preview =
            File(
                File(context.filesDir, DocumentStorageImpl.THUMBNAILS_DIR),
                "$originalName.${DocumentStorageImpl.THUMBNAIL_EXTENSION}",
            )
        return if (preview.exists()) ContentRef(preview.path) else null
    }
}
