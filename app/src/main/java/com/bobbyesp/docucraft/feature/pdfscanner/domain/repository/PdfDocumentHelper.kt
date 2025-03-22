package com.bobbyesp.docucraft.feature.pdfscanner.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

interface PdfDocumentHelper {
    /**
     * Saves a specific page from a PDF file as an image.
     *
     * This function extracts a single page from the provided PDF file (referenced by a URI) and
     * saves it as an image in the specified output file. It supports various image formats and
     * quality settings.
     *
     * @param pdfUri The URI of the PDF file from which to extract the page. The URI should point to
     *   a locally accessible PDF file.
     * @param outputFile The file where the extracted page image will be saved. This file will be
     *   created or overwritten.
     * @param pageIndex The index of the page to extract (0-based). For example, 0 represents the
     *   first page, 1 the second, and so on. If the index is out of bounds (less than 0 or greater
     *   than/equal to the total number of pages in the PDF), a `RuntimeException` will be thrown
     *   during the PDF rendering process.
     * @param format The image format to use for saving (e.g., [Bitmap.CompressFormat.PNG],
     *   [Bitmap.CompressFormat.JPEG], [Bitmap.CompressFormat.WEBP]). Defaults to
     *   [Bitmap.CompressFormat.WEBP].
     * @param quality The quality of the image compression (0-100, where 100 is the best quality).
     *   This parameter is only applicable for lossy formats like [Bitmap.CompressFormat.JPEG] and
     *   [Bitmap.CompressFormat.WEBP]. For lossless formats like [Bitmap.CompressFormat.PNG], this
     *   parameter is ignored. Defaults to 80.
     * @throws IllegalArgumentException if the `outputFile`'s parent directory does not exist or is
     *   not a directory, or if the specified format is not a supported [Bitmap.CompressFormat].
     * @throws RuntimeException if there is an error during the PDF page extraction or image saving
     *   process, including if the `pdfUri` is invalid or if the `pageIndex` is out of bounds. Also
     *   throws an exception if any IO error happens during the saving process.
     */
    fun savePdfPageAsImage(
        pdfUri: Uri, // Now accepts a Uri
        outputFile: File,
        pageIndex: Int,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.WEBP,
        quality: Int = 80,
    )
}
