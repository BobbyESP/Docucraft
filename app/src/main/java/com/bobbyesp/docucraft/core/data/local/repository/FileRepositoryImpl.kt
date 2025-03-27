package com.bobbyesp.docucraft.core.data.local.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.bobbyesp.docucraft.core.domain.repository.FileRepository
import kotlinx.io.IOException

class FileRepositoryImpl(private val context: Context) : FileRepository {

    override suspend fun readBytesFromUri(uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            // Use buffered input stream for better performance with larger files
            inputStream.buffered().use { buffered -> buffered.readBytes() }
        } ?: throw IOException("Failed to read bytes from URI: $uri")
    }

    override fun getFilePathFromUri(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> uri.path
                "content" -> {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val columnIndex = it.getColumnIndex("_data")
                            if (columnIndex != -1) {
                                return it.getString(columnIndex)
                            }
                        }
                    }
                    null
                }
                else -> throw IllegalArgumentException("Unsupported URI scheme: ${uri.scheme}")
            }
        } catch (e: Exception) {
            Log.e("FileRepository", "Error extracting file path: ${e.message}", e)
            null
        }
    }
}
