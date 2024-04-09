package com.bobbyesp.docucraft.data.local.repository

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

class FileHelper(private val context: Context) {
    fun getFileFromContentResolver(context: Context, uri: Uri): File? {
        return try {
            val fd = context.contentResolver.openFileDescriptor(uri, "r")
            val filePath = fd?.fileDescriptor?.let { File(it.toString()) }
            fd?.close()
            filePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    fun deleteFile(file: File): Boolean {
        return try {
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    fun moveFileToDownloads(fileUri: Uri, fileName: String): Uri? {
        val sourceFile = File(fileUri.path ?: throw IllegalStateException("File path must not be null"))
        val destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        val destFile = File(destDir, fileName)

        return try {
            sourceFile.copyTo(destFile, overwrite = true)
            updateMediaStore(destFile)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun updateMediaStore(file: File): Uri? {
        val contentResolver: ContentResolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, file.name)
            put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.Files.FileColumns.DATA, file.absolutePath)
        }

        return contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
    }
}