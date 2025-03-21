package com.bobbyesp.docucraft.core.util

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FileSizeParser {
    fun parseFileSize(size: Long): String {
        if (size <= 0) return "0 B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
        val digitGroups = minOf((log10(size.toDouble()) / log10(1024.0)).toInt(), units.size - 1)

        val scaledSize = size / 1024.0.pow(digitGroups.toDouble())

        // Format with 2 decimal places for larger units, none for bytes
        return when {
            digitGroups == 0 -> "$size B"
            scaledSize % 1 == 0.0 -> "${scaledSize.toInt()} ${units[digitGroups]}"
            else -> String.format(Locale.getDefault(), "%.2f %s", scaledSize, units[digitGroups])
        }
    }
}