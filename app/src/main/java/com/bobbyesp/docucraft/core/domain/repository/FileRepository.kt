package com.bobbyesp.docucraft.core.domain.repository

import android.net.Uri
import com.bobbyesp.docucraft.core.domain.FileDestination
import java.io.File

interface FileRepository {
    suspend fun saveFile(file: File, destination: FileDestination)

    suspend fun moveFile(file: File, destination: FileDestination)

    suspend fun deleteFile(file: File)

    suspend fun readBytesFromUri(uri: Uri): ByteArray
}