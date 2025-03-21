package com.bobbyesp.docucraft.feature.pdfscanner.domain.repository

import android.net.Uri
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.flow.Flow
import java.util.UUID

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
     * Saves a PDF document obtained from a document scan result to the application's internal storage.
     *
     * This function takes a `GmsDocumentScanningResult.Pdf` object and a filename (or generates a UUID if no filename is provided),
     * then writes the PDF data to a file in the app's files directory.
     *
     * @param scanPdfResult The PDF result obtained from the document scanner.
     * @param filename The desired filename for the saved PDF. If not provided, a UUID will be generated and used.
     * @throws IllegalStateException If the application's files directory cannot be accessed or if there are issues creating/writing the file.
     * @throws Exception If any other error occurs during the file saving process.
     */
    suspend fun savePdf(
        scanPdfResult: GmsDocumentScanningResult.Pdf,
        filename: String = UUID.randomUUID().toString()
    )

    /**
     * Deletes a PDF file from storage and database using its URI.
     *
     * This function performs the following actions:
     * 1. **Retrieves PDF Information:** Fetches the ScannedPdf record from the database using the URI.
     * 2. **Verifies File Existence:** Checks if the file exists at the specified path.
     * 3. **Deletes the File:** If the file exists, it attempts to delete the file from the file system.
     * 4. **Removes Database Record:** Removes the PDF entry from the database.
     * 5. **Handles Errors:** Logs and propagates any errors that occur during the process.
     *
     * **Note:** This function is a suspending function and should be called within a coroutine or
     * from another suspending function.
     *
     * @param pdfPath The URI of the PDF to be deleted.
     *
     * @throws IllegalArgumentException If the provided URI is invalid or not found in the database.
     * @throws IOException If there's an error accessing or deleting the file.
     * @throws Exception If some other error occurs during file deletion.
     *
     * @sample
     *  ```kotlin
     *    // Assuming you have a URI for the PDF
     *    try {
     *        deletePdf(pdfUri)
     *        println("PDF deleted successfully")
     *    } catch (e: Exception) {
     *        println("Error deleting PDF: ${e.message}")
     *    }
     */
    suspend fun deletePdf(pdfPath: Uri)

    /**
     * Shares a PDF file located at the specified URI.
     *
     * This function creates an implicit intent to share the PDF file with other applications
     * that can handle the `application/pdf` MIME type. It utilizes a `FileProvider` to
     * grant temporary read permissions to the receiving application.
     *
     * @param pdfPath The URI representing the location of the PDF file to be shared.
     *                This should be a valid content URI provided by a `FileProvider` or
     *                a similar mechanism if the file is not accessible via a simple file path.
     *                If sharing from external storage ensure appropriate permission is granted
     *                (e.g. READ_EXTERNAL_STORAGE in the manifest).
     * @throws IllegalArgumentException if the `pdfPath` is null or if the URI is not a content URI and
     *                                  FileProvider is necessary (e.g. sharing a file from internal
     *                                  storage). Note that you would have to handle the case where
     *                                  `pdfPath` is not a valid file as well. This function does not check
     *                                  for file validity.
     * @throws android.content.ActivityNotFoundException if there are no applications that can handle the share intent.
     *
     * @sample
     * ```kotlin
     * // Example using a FileProvider and sharing from internal storage:
     * val pdfFile = File(context.filesDir, "my_document.pdf")
     * // ... (write content to pdfFile) ...
     * val pdfUri: Uri = FileProvider.getUriForFile(
     *     context,
     *     "com.yourpackage.fileprovider", // Replace with your authority
     *     pdfFile
     * )
     * sharePdf(pdfUri)
     * ```
     *
     * ```kotlin
     * // Example sharing a file from external storage (Requires appropriate permissions):
     * val pdfPath = Uri.fromFile(File("/storage/emulated/0/Download/my_document.pdf"))
     * sharePdf(pdfPath)
     * ```
     */
    fun sharePdf(pdfPath: Uri)

    /**
     * Opens a PDF file in an external PDF viewer application.
     *
     * This function takes the URI of a PDF file as input and attempts to open it
     * using an installed PDF viewer on the device. It handles different URI schemes
     * and gracefully handles cases where no suitable viewer is found.
     *
     * @param pdfPath The URI of the PDF file to be opened. This can be a file URI
     *                (e.g., "file:///path/to/file.pdf") or a content URI
     *                (e.g., "content://authority/path").
     *                If the uri is a file uri, it is internally converted to a content uri for security reasons.
     * @throws Exception if there is issue creating content uri from file path.
     * @throws android.content.ActivityNotFoundException if no suitable PDF viewer
     *                                                 application is installed on
     *                                                 the device.
     */
    fun openPdfInViewer(pdfPath: Uri)
}