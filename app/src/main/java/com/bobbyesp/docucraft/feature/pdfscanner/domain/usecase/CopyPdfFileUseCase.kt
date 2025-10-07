package com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase

import android.content.Context
import android.net.Uri
import com.bobbyesp.docucraft.core.util.ensureParent
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.sink
import kotlinx.io.buffered

/**
 * Use case for copying a PDF file from a URI to local storage. Single responsibility: file copying
 * operation.
 */
class CopyPdfFileUseCase(private val context: Context) {
    suspend operator fun invoke(inputUri: Uri, outputFile: PlatformFile) {
        // Ensure parent directory exists
        outputFile.ensureParent(mustCreate = true)

        val sink = outputFile.sink(append = false).buffered()

        sink.use { bufferedSink ->
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    bufferedSink.write(buffer, 0, bytesRead)
                }
            } ?: throw IllegalStateException("Could not open input stream for URI: $inputUri")
        }
    }

    companion object {
        private const val BUFFER_SIZE = 8192 // 8KB buffer
    }
}
