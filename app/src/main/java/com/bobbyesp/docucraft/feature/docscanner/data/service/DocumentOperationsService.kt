/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.data.service

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.IntRange
import java.io.File

/**
 * Renders pages of a document as images.
 *
 * An implementation detail of `DocumentStorageImpl` rather than a domain concept: it speaks Bitmap
 * and File, which is exactly why it belongs on this side of the line.
 */
interface DocumentOperationsService {
    /**
     * Renders one page of a document into [outputFile], overwriting whatever is there.
     *
     * [format] and [quality] have no defaults on purpose. A default encoding is how the previews
     * ended up being WEBP inside files named `.png`: the caller chose the name and let the format
     * choose itself, and nothing made them agree.
     *
     * @param documentUri A locally readable `file:` or `content:` document.
     * @param pageIndex Zero-based. Out of range is a failure, not a crash.
     * @param quality Ignored by lossless formats.
     * @return Whether an image was actually written. Failures are logged rather than thrown, since
     *   a missing preview is not worth failing a scan over — but the caller still has to be able to
     *   tell, or it will record a path to a file that is not there.
     */
    fun saveDocumentPageAsImage(
        documentUri: Uri,
        outputFile: File,
        pageIndex: Int,
        format: Bitmap.CompressFormat,
        @IntRange(from = 0, to = 100) quality: Int,
    ): Boolean
}
