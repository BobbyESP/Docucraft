package com.bobbyesp.docucraft.core.data.local.repository

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.bobbyesp.docucraft.core.domain.FileDestination
import com.bobbyesp.docucraft.core.domain.repository.FileRepository
import kotlinx.io.IOException
import java.io.File
import java.io.FileOutputStream

class FileRepositoryImpl(
    private val context: Context
) : FileRepository {

    /**
     * Saves a file to the specified destination.
     *
     * @param context The application context.
     * @param sourceFile The source file to be saved.
     * @param destination The destination where the file should be saved.
     * @throws IOException If the source file does not exist or is not a file.
     * @throws IllegalArgumentException If the external storage destination is not a directory.
     */
    override suspend fun saveFile(
        sourceFile: File, destination: FileDestination
    ) {
        if (!sourceFile.exists() || !sourceFile.isFile) {
            throw IOException("Source file does not exist or is not a file: ${sourceFile.absolutePath}")
        }

        val targetFile = when (destination) {
            is FileDestination.AppContentProvider -> {
                val targetDir =
                    destination.subDirectory?.let { File(context.filesDir, it) } ?: context.filesDir
                if (!targetDir.exists()) targetDir.mkdirs()
                File(targetDir, sourceFile.name)
            }

            is FileDestination.ExternalStorage -> {
                with(destination.directory) {
                    if (!exists()) mkdirs()
                    if (!isDirectory) {
                        throw IllegalArgumentException("External storage destination is not a directory: ${destination.directory}")
                    }
                    File(this, sourceFile.name)
                }
            }

            is FileDestination.CacheStorage -> File(context.cacheDir, sourceFile.name)
        }

        copyFile(source = sourceFile, target = targetFile)
    }

    override suspend fun moveFile(
        file: File, destination: FileDestination
    ) {
        saveFile(file, destination)
        deleteFile(file)
    }

    override suspend fun deleteFile(file: File) {
        val uri = file.toUri()
        val rowsDeleted = context.contentResolver.delete(uri, null, null)
        if (rowsDeleted == 0) {
            throw IOException("Failed to delete file: ${file.absolutePath}")
        }
    }

    override suspend fun readBytesFromUri(uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            // Use buffered input stream for better performance with larger files
            inputStream.buffered().use { buffered ->
                buffered.readBytes()
            }
        } ?: throw IOException("Failed to read bytes from URI: $uri")
    }

    /**
     * Copies the content from a source file to a target file.
     * @param source The source file.
     * @param target The target file.
     * @throws IOException If an I/O error occurs during the copy operation.
     */
    @Throws(IOException::class)
    private fun copyFile(source: File, target: File) {
        context.contentResolver.openInputStream(source.toUri())?.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }
}