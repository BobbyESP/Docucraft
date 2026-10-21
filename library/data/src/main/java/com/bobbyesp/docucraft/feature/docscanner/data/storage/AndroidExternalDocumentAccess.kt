/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.ExternalDocumentAccess
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.MeasuredFile
import com.bobbyesp.scanner.ContentRef
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Other apps' files through the `ContentResolver`, and their permissions through its grants. */
class AndroidExternalDocumentAccess(context: Context) : ExternalDocumentAccess {

    private val resolver = context.applicationContext.contentResolver

    override fun keep(document: ContentRef): Boolean {
        val uri = document.value.toUri()
        // A file path is not lent: there is nothing to keep.
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false

        // A provider that does not offer it says so by throwing.
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Asked, not assumed from the call above: what counts is whether the grant is held.
        return resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }

    override fun release(document: ContentRef) {
        runCatching {
            resolver.releasePersistableUriPermission(
                document.value.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    override suspend fun measure(document: ContentRef): MeasuredFile? =
        withContext(Dispatchers.IO) {
            runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                val input =
                    resolver.openInputStream(document.value.toUri()) ?: return@runCatching null
                input.use { stream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        digest.update(buffer, 0, read)
                        size += read
                    }
                }
                MeasuredFile(size, digest.digest().joinToString("") { "%02x".format(it) })
            }
                .getOrNull()
        }

    private companion object {
        const val BUFFER_SIZE = 8192
    }
}
