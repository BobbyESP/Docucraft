package com.bobbyesp.utilities

import android.content.Context
import java.io.File
import kotlin.math.log10
import kotlin.math.pow

object Files {
    fun getTempDirectory(context: Context): File {
        return context.cacheDir
    }

    fun createTempFile(context: Context, prefix: String, suffix: String): File {
        return File.createTempFile(prefix, suffix, getTempDirectory(context))
    }

    fun deleteTempFile(context: Context, prefix: String, suffix: String) {
        val tempFile = File(getTempDirectory(context), prefix + suffix)
        tempFile.delete()
    }

    fun deleteTempFile(context: Context, fileName: String) {
        val tempFile = File(getTempDirectory(context), fileName)
        tempFile.delete()
    }
}

fun parseFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()

    return String.format(
        "%.2f %s",
        bytes / 1024.0.pow(digitGroups.toDouble()),
        units[digitGroups]
    )
}
