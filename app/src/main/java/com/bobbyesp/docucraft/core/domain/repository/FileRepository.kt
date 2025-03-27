package com.bobbyesp.docucraft.core.domain.repository

import android.net.Uri

interface FileRepository {
    suspend fun readBytesFromUri(uri: Uri): ByteArray

    fun getFilePathFromUri(uri: Uri): String?
}
