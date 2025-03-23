package com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.usecase

import android.net.Uri
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface ScannedPdfUseCase {
    suspend fun allScannedPdfsFlow(): Flow<List<ScannedPdf>>

    suspend fun saveScannedPdf(
        scanPdfResult: GmsDocumentScanningResult.Pdf,
        filename: String = UUID.randomUUID().toString()
    )

    suspend fun modifyPdf(pdfId: String, title: String?, description: String?)

    suspend fun deleteScannedPdf(pdfPath: Uri)

    fun sharePdf(pdfPath: Uri)

    fun openPdfInViewer(pdfPath: Uri)
}