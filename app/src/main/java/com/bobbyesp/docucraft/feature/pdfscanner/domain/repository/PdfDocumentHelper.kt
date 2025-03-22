package com.bobbyesp.docucraft.feature.pdfscanner.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

interface PdfDocumentHelper {
    /**
     * Saves a specific page from a PDF file as an image.
     *
     * This function extracts a single page from the provided PDF file and saves it as an image in
     * the specified output file. It supports various image formats and quality settings.
     *
     * @param pdfFile The PDF file from which to extract the page.
     * @param outputFile The file where the extracted page image will be saved.
     * @param pageIndex The index of the page to extract (0-based). Defaults to 0 (the first page).
     *   If the index is out of bounds (less than 0 or greater than/equal to the number of pages),
     *   an exception will be thrown during PDF rendering process.
     * @param format The image format to use for saving (e.g., PNG, JPEG, WEBP). Defaults to
     *   [Bitmap.CompressFormat.PNG].
     * @param quality The quality of the image compression (0-100, where 100 is the best quality).
     *   Only applicable for lossy formats like JPEG and WEBP. Defaults to 100.
     * @throws IllegalArgumentException if the `pdfFile` or `outputFile` does not exist, or if the
     *   format is not a supported format.
     * @throws RuntimeException if there is an error during the PDF page extraction or image saving
     *   process.
     */
    fun savePdfPageAsImage(
        pdfUri: Uri, // Now accepts a Uri
        outputFile: File,
        pageIndex: Int,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 80,
    )
}
