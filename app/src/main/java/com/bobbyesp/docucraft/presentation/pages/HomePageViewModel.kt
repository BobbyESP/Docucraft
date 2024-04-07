package com.bobbyesp.docucraft.presentation.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import com.bobbyesp.docucraft.App.Companion.APP_FILE_PROVIDER
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.domain.model.SavedPdf
import com.bobbyesp.utilities.Logging
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomePageViewModel @Inject constructor(
    @ApplicationContext applicationContext: Context
) : ViewModel() {

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

        if(outputFile.exists()) {
            Logging.i("Requested PDF file already exists!")
        }

        return try {
            val outputStream = FileOutputStream(outputFile)
            context.contentResolver.openInputStream(pdf.uri)?.use { pdfByteStream ->
                pdfByteStream.copyTo(outputStream)
            }

            val documentPath = FileProvider.getUriForFile(
                context,
                APP_FILE_PROVIDER,
                outputFile
            )
            SavedPdf(fileName = filename, title = filename, path = documentPath, description = null, fileSizeBytes = null, pageCount = pdf.pageCount, savedTimestamp = System.currentTimeMillis())
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
}