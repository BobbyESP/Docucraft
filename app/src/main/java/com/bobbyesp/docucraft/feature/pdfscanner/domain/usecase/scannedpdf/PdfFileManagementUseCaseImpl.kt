package com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase.scannedpdf

import android.content.Context
import android.net.Uri
import com.bobbyesp.docucraft.core.util.ensureParent
import com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.usecase.PdfFileManagementUseCase
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.sink
import kotlin.use
import kotlinx.io.buffered

class PdfFileManagementUseCaseImpl(private val context: Context) : PdfFileManagementUseCase {
    override suspend fun copyToSystemStorage(inputUri: Uri, outputFile: PlatformFile) {
        // Ensure directory exists
        outputFile.ensureParent(mustCreate = true)

        val sink = outputFile.sink(append = false).buffered()

        sink.use { bufferedSink ->
            // Use contentResolver to open an input stream from the Uri
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                val buffer = ByteArray(8192) // 8KB buffer for efficient transfer
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    bufferedSink.write(buffer, 0, bytesRead)
                }
            } ?: throw IllegalStateException("Could not open input stream for URI: $inputUri")
        }
    }
}
