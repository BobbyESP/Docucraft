package com.bobbyesp.docucraft.feature.pdfscanner.data.local.repository

import android.content.Context
import android.net.Uri
import com.bobbyesp.docucraft.core.domain.repository.FileRepository
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.dao.ScannedPdfDao
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

class ScannedPdfRepositoryImpl(
    private val context: Context,
    private val fileRepository: FileRepository,
    private val scannedPdfDao: ScannedPdfDao
) : ScannedPdfRepository {
    override suspend fun savePdf(scanPdfResult: GmsDocumentScanningResult.Pdf, filename: String) {
        // TODO("Not yet implemented")
    }

    override suspend fun deletePdf(scannedPdf: ScannedPdf) {
        // TODO("Not yet implemented")
    }

    override fun sharePdf(pdfPath: Uri) {
        // TODO("Not yet implemented")
    }

    override fun openPdfInViewer(pdfPath: Uri) {
        // TODO("Not yet implemented")
    }
}