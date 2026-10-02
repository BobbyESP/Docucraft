/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.db

import java.io.ByteArrayOutputStream

/**
 * Where a document catalogued before schema version 5 is, relative to the app's files directory.
 *
 * Up to version 4 the catalogue kept the `FileProvider` URI of each document, such as
 * `content://com.bobbyesp.docucraft.fileprovider/scanned-pdfs/Scan_20260919_142530.pdf`. That
 * depends on the provider's authority, which differs between the release and debug builds, and on a
 * name in the provider's XML. The file was always `scans/pdf/<name>.pdf`, which is what is kept
 * from version 5 on.
 *
 * Plain Kotlin, with its own decoder instead of `android.net.Uri`, so it can be tested on the JVM.
 */
internal object LegacyDocumentPath {

    /** The folder those documents were saved in. */
    const val DIRECTORY = "scans/pdf"

    /** The name `FileProvider` gave that folder in its URIs. */
    private const val PROVIDER_ROOT = "scanned-pdfs"

    private const val CONTENT_SCHEME = "content://"

    /**
     * The relative path of the document at [location], or `null` when [location] is not one of the
     * app's own URIs for a document.
     */
    fun relativePathOf(location: String): String? {
        if (!location.startsWith(CONTENT_SCHEME)) return null
        // After the authority, whichever it is: "/scanned-pdfs/<name, percent-encoded>".
        val path =
            location
                .substring(CONTENT_SCHEME.length)
                .substringAfter('/', missingDelimiterValue = "")
        val name = path.removePrefix("$PROVIDER_ROOT/")
        if (name == path || name.isEmpty()) return null
        return "$DIRECTORY/${percentDecode(name)}"
    }

    /**
     * The relative path of the document at [location]. When [location] is not recognized, the path
     * its [filename] was always saved under.
     */
    fun relativePathOf(location: String, filename: String): String =
        relativePathOf(location) ?: "$DIRECTORY/$filename.pdf"

    /** Undoes the percent-encoding of a URI path, reading the bytes as UTF-8. */
    private fun percentDecode(encoded: String): String {
        if ('%' !in encoded) return encoded

        val bytes = ByteArrayOutputStream(encoded.length)
        var index = 0
        while (index < encoded.length) {
            val byte = if (encoded[index] == '%') encoded.hexByteAt(index + 1) else null
            if (byte != null) {
                bytes.write(byte)
                index += 3
            } else {
                // Not an escape: the character itself, whole even when it takes two chars.
                val codePoint = encoded.codePointAt(index)
                bytes.write(String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8))
                index += Character.charCount(codePoint)
            }
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun String.hexByteAt(index: Int): Int? {
        if (index + 1 >= length) return null
        val high = Character.digit(this[index], 16)
        val low = Character.digit(this[index + 1], 16)
        return if (high < 0 || low < 0) null else high * 16 + low
    }
}
