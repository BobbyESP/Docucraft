package com.bobbyesp.docucraft.feature.pdfscanner.domain.repository

import android.net.Uri
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.util.UUID

interface ScannedPdfRepository {
    suspend fun savePdf(
        scanPdfResult: GmsDocumentScanningResult.Pdf,
        filename: String = UUID.randomUUID().toString()
    )

    suspend fun deletePdf(scannedPdf: ScannedPdf)

    fun sharePdf(pdfPath: Uri)

    fun openPdfInViewer(pdfPath: Uri)
}