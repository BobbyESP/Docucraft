package com.bobbyesp.docucraft.presentation.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.App.Companion.APP_FILE_PROVIDER
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.data.local.db.daos.SavedPDFsDao
import com.bobbyesp.docucraft.domain.model.SavedPdf
import com.bobbyesp.utilities.Logging
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomePageViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val savedPDFsDao: SavedPDFsDao
) : ViewModel() {
    private val mutableSavedPdfs = MutableStateFlow<List<SavedPdf>>(emptyList())
    val savedPdfs = mutableSavedPdfs.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                savedPDFsDao.getAllSavedPDFsAsFlow()
                    .map { savedPdfEntities ->
                        savedPdfEntities.map { savedPdfEntity ->
                            savedPdfEntity.toSavedPdf()
                        }
                    }
                    .collect { mappedSavedPdfs ->
                        mutableSavedPdfs.value = mappedSavedPdfs
                    }
            } catch (e: Exception) {
                Logging.e(e)
            }
        }
    }
    fun savePdf(
        context: Context,
        pdf: GmsDocumentScanningResult.Pdf,
        filename: String = UUID.randomUUID().toString()
    ): SavedPdf {
        val outputDir = File(context.filesDir, "scans/pdf")

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val outputFile = File(outputDir, "$filename.pdf")

        if (outputFile.exists()) {
            Logging.i("Requested PDF file already exists!")
        }

        return try {
            val outputStream = FileOutputStream(outputFile)
            context.contentResolver.openInputStream(pdf.uri)?.use { pdfByteStream ->
                pdfByteStream.copyTo(outputStream)
            }

            val fileSizeBytes = outputFile.length()

            val documentPath = FileProvider.getUriForFile(
                context,
                APP_FILE_PROVIDER,
                outputFile
            )

            SavedPdf(
                fileName = filename,
                title = filename,
                path = documentPath,
                description = null,
                fileSizeBytes = fileSizeBytes,
                pageCount = pdf.pageCount,
                savedTimestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Logging.e(e)
            SavedPdf.emptyPdf()
        }
    }

    fun openPdfInViewer(context: Context, pdfPath: Uri) {
        val viewIntent = Intent(Intent.ACTION_VIEW)
        viewIntent.setDataAndType(pdfPath, "application/pdf")
        viewIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(
            context,
            Intent.createChooser(
                viewIntent,
                context.getString(R.string.open_pdf)
            ),
            null
        )
    }

    fun sharePdf(context: Context, pdfPath: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.putExtra(Intent.EXTRA_STREAM, pdfPath)
        shareIntent.setDataAndType(pdfPath, "application/pdf")
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(
            context,
            Intent.createChooser(
                shareIntent,
                context.getString(R.string.open_pdf)
            ),
            null
        )
    }

    fun deletePdf(savedPdf: SavedPdf) {

    }

    suspend fun savePdfToDatabase(pdf: SavedPdf) {
        savedPDFsDao.insert(pdf.toSavedPdfEntity())
    }

    fun deletePdfFromDatabase(pdf: SavedPdf) {
        savedPDFsDao.deletePDFByTimestamp(pdf.savedTimestamp)
    }
}