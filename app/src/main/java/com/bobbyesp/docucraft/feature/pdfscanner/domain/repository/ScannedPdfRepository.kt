package com.bobbyesp.docucraft.feature.pdfscanner.domain.repository

import android.net.Uri
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.entity.ScannedPdfEntity
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import kotlinx.coroutines.flow.Flow

interface ScannedPdfRepository {

    /**
     * Retrieves a flow of all scanned PDFs available in the system.
     *
     * This function asynchronously fetches a list of `ScannedPdf` objects
     * and emits them as a stream of data through a `Flow`.
     *
     * The flow will emit a new list of `ScannedPdf` whenever the underlying
     * data changes, for example, when a new PDF is scanned or an existing
     * PDF is deleted.
     *
     * **Note:** This function operates in a suspend context, meaning it can be
     * safely called from within a coroutine or another suspend function.
     *
     * @return A `Flow` that emits lists of `ScannedPdf` objects. Each emission
     *         represents the current state of all scanned PDFs.
     *
     * @see ScannedPdf
     * @see Flow
     */
    suspend fun getAllScannedPdfsFlow(): Flow<List<ScannedPdf>>

    /**
     * Retrieves a scanned PDF by its unique identifier.
     *
     * This function fetches a ScannedPdf object based on the provided `pdfId`.
     * It is a suspending function, meaning it can be safely called within a coroutine
     * and will suspend execution until the PDF is retrieved or an error occurs.
     *
     * @param pdfId The unique identifier of the scanned PDF to retrieve. Must not be null or empty.
     * @return The [ScannedPdf] object corresponding to the provided `pdfId`.
     * @throws IllegalArgumentException if the `pdfId` is null or empty.
     * @throws NoSuchElementException if no ScannedPdf with the given `pdfId` exists.
     * @throws Exception if any other error occurs during the retrieval process.
     */
    suspend fun getScannedPdfById(pdfId: String): ScannedPdf

    suspend fun savePdf(scannedPdf: ScannedPdfEntity)

    suspend fun modifyTitleAndDescription(pdfId: String, title: String?, description: String?)

    suspend fun deletePdf(pdfPath: Uri)

    fun sharePdf(pdfPath: Uri)

    fun openPdfInViewer(pdfPath: Uri)
}
