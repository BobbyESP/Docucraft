package com.bobbyesp.docucraft.feature.pdfscanner.data.local.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.dao.ScannedPdfDao
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.entity.ScannedPdfEntity
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf.Companion.toModel
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.ScannedPdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class ScannedPdfRepositoryImpl(
    private val context: Context,
    private val scannedPdfDao: ScannedPdfDao,
) : ScannedPdfRepository {

    override suspend fun getAllScannedPdfsFlow(): Flow<List<ScannedPdf>> =
        scannedPdfDao
            .getAllPdfsFlow()
            .map { entities -> entities.map { it.toModel() } }
            .flowOn(Dispatchers.IO)

    override suspend fun getScannedPdfById(pdfId: String): ScannedPdf {
        if(pdfId.isEmpty()) {
            throw IllegalArgumentException("PDF ID must not be empty")
        }
        val entity = scannedPdfDao.fetchById(pdfId) ?: throw NoSuchElementException("No PDF found with ID: $pdfId")
        return entity.toModel()
    }

    override suspend fun savePdf(
        scannedPdf: ScannedPdfEntity
    ) {
        try {
            scannedPdfDao.insert(scannedPdf)
        } catch (e: Exception) {
            throw RuntimeException("Failed to save PDF: ${e.message}", e)
        }
    }

    override suspend fun modifyTitleAndDescription(
        pdfId: String,
        title: String?,
        description: String?
    ) {
        if (pdfId.isEmpty()) {
            throw IllegalArgumentException("PDF ID must not be empty")
        }

        val updatedCount = scannedPdfDao.updateTitleAndDescription(pdfId, title, description)

        if (updatedCount <= 0) {
            throw NoSuchElementException("No PDF found with ID: $pdfId")
        }
    }

    override suspend fun deletePdf(pdfPath: Uri) {
        // First remove from database to maintain referential integrity
        val deletedCount = scannedPdfDao.deleteByPath(pdfPath.toString())

        if (deletedCount <= 0) {
            throw IllegalArgumentException("No PDF found with path: $pdfPath")
        }
    }

    override fun sharePdf(pdfPath: Uri) {
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, pdfPath)
                setDataAndType(pdfPath, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(
            Intent.createChooser(shareIntent, context.getString(R.string.share_pdf))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    override fun openPdfInViewer(pdfPath: Uri) {
        val viewIntent =
            Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(pdfPath, "application/pdf")
            }

        context.startActivity(
            Intent.createChooser(viewIntent, context.getString(R.string.open_pdf_in_viewer))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        private const val TAG = "ScannedPdfRepository"
    }
}
