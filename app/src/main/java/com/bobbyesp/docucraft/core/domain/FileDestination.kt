package com.bobbyesp.docucraft.core.domain

import java.io.File

/**
 * Represents the destination where a file should be saved.
 */
sealed class FileDestination {
    /**
     * Saves the file to the application's internal storage, in the "files" directory.
     * @param subDirectory Optional subdirectory within the "files" directory.
     */
    data class AppContentProvider(val subDirectory: String? = null) : FileDestination()

    /**
     * Saves the file to a specific directory in external storage.
     * @param directory The absolute path of the external directory.
     */
    data class ExternalStorage(val directory: File) : FileDestination()

    /**
     * Save the file to cache directory.
     */
    object CacheStorage: FileDestination()
}