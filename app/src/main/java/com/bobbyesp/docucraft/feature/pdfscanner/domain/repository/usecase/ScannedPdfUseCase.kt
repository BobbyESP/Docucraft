package com.bobbyesp.docucraft.feature.pdfscanner.domain.repository.usecase

import android.net.Uri
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface ScannedPdfUseCase {
    /**
     * Emits a flow of lists of `ScannedPdf` objects.
     *
     * This function provides a stream of data representing the currently available scanned PDFs.
     * The list emitted by the flow may change over time, reflecting additions, deletions, or
     * updates to the scanned PDF data.
     *
     * This is intended for scenarios where you need to reactively observe changes to the collection
     * of scanned PDFs, such as updating a UI list whenever a new PDF is scanned or an existing one is removed.
     *
     * The flow emits a new list each time there is a change in the underlying scanned PDF data.
     *
     * Note: This is a suspend function, meaning it should be called from within a coroutine.
     *
     * @return A [Flow] that emits a [List] of [ScannedPdf] objects. Each list represents the
     *         current state of the scanned PDFs at the time of emission.
     *
     * Example Usage:
     * ```kotlin
     * lifecycleScope.launch {
     *   scannedPdfsListFlow().collect { scannedPdfs ->
     *     // Update the UI with the latest list of scanned PDFs
     *     updatePdfListUi(scannedPdfs)
     *   }
     * }
     * ```
     *
     * @see ScannedPdf
     */
    suspend fun scannedPdfsListFlow(): Flow<List<ScannedPdf>>

    /**
     * Retrieves a scanned PDF document by its unique identifier.
     *
     * This function simulates fetching a scanned PDF from a data source (e.g., a server or local database).
     * It uses a suspend function, meaning it can be paused and resumed, allowing for asynchronous
     * operations without blocking the main thread.
     *
     * @param pdfId The unique identifier of the scanned PDF to retrieve.
     * @return A [ScannedPdf] object representing the scanned PDF document.
     * @throws IllegalArgumentException if the provided `pdfId` is null or empty.
     * @throws NoSuchElementException if no scanned PDF is found with the given `pdfId`.
     * @throws Exception if any other error occurs during the retrieval process.
     */
    suspend fun getScannedPdf(pdfId: String): ScannedPdf

    /**
     * Retrieves a [ScannedPdf] object representing a PDF document located at the specified [pdfPath].
     *
     * This function performs the necessary operations to access and process the PDF file.
     * It is designed to be used within a coroutine scope due to its `suspend` modifier, allowing
     * for asynchronous handling of file operations and potential long-running tasks.
     *
     * @param pdfPath The [Uri] representing the location of the PDF file. This can be a content Uri, a file Uri, etc.
     * @return A [ScannedPdf] object containing the details and content of the scanned PDF.
     * @throws Exception If there's any issue accessing or processing the PDF file. For example if the Uri is invalid, or if the file is corrupted.
     * @sample
     *   val uri = Uri.parse("content://com.example.provider/files/document.pdf")
     *   try {
     *       val scannedPdf = getScannedPdf(uri)
     *       // Use the scannedPdf object
     *       println("Scanned PDF: ${scannedPdf.name}")
     *   } catch (e: Exception) {
     *       println("Error scanning PDF: ${e.message}")
     *   }
     *
     *
     * @see ScannedPdf
     * @see Uri
     */
    suspend fun getScannedPdf(pdfPath: Uri): ScannedPdf

    /**
     * Saves a scanned PDF document to a file.
     *
     * This function takes a [GmsDocumentScanningResult.Pdf] object, representing the result of a PDF scan,
     * and saves it to a file with the specified filename. If no filename is provided, a UUID will be used
     * to generate a unique filename.
     *
     * The function is marked as `suspend`, meaning it must be called within a coroutine or another
     * suspending function. This is likely due to the underlying file I/O operations that may take
     * some time to complete.
     *
     * @param scanPdfResult The [GmsDocumentScanningResult.Pdf] object containing the scanned PDF data.
     *                      This object holds the necessary information to write the PDF to a file.
     * @param filename The desired filename for the saved PDF. If not provided, a unique UUID will be used.
     *                 Defaults to a randomly generated UUID string.
     * @throws Exception If any error occurs during the file saving process, such as an I/O error.
     */
    suspend fun saveScannedPdf(
        scanPdfResult: GmsDocumentScanningResult.Pdf,
        filename: String = UUID.randomUUID().toString(),
    )

    /**
     * Modifies an existing PDF document with the given ID.
     *
     * This function updates the title and/or description of a PDF identified by its `pdfId`.
     * If either `title` or `description` is null, that field will not be updated.
     *
     * @param pdfId The unique identifier of the PDF to modify. This must correspond to a valid PDF in the system.
     * @param title The new title to assign to the PDF. If null, the title will remain unchanged.
     * @param description The new description to assign to the PDF. If null, the description will remain unchanged.
     *
     * @throws IllegalArgumentException if `pdfId` is blank or if the pdf is not found.
     * @throws Exception if any error occurs during the modification process, such as database errors or file system issues.
     *
     * @sample
     * ```
     *  // Example usage:
     *  try {
     *      modifyPdf("some-pdf-id", "New Title", "Updated Description")
     *      println("PDF modified successfully.")
     *  } catch (e: IllegalArgumentException) {
     *      println("Invalid input: ${e.message}")
     *  } catch (e: Exception) {
     *      println("Error modifying PDF: ${e.message}")
     *  }
     *
     *  try{
     *      modifyPdf("some-pdf-id", null, "Only changed Description")
     *  }catch(e : Exception){
     *      println("Error modifying PDF: ${e.message}")
     *  }
     *
     *  try{
     *      modifyPdf("some-pdf-id", "Only changed title", null)
     *  }catch(e : Exception){
     *      println("Error modifying PDF: ${e.message}")
     *  }
     * ```
     */
    suspend fun modifyPdf(pdfId: String, title: String?, description: String?)

    /**
     * Deletes a scanned PDF file from the device's storage.
     *
     * This function attempts to delete the PDF file located at the specified URI.
     * It is a suspend function, meaning it should be called from within a coroutine
     * or another suspend function, as file operations can potentially be long-running.
     *
     * Note: This function assumes the app has the necessary permissions to access
     * and delete the file at the given URI. If the app does not have the correct
     * permissions, or if the file does not exist, the deletion may silently fail
     * (without throwing an exception).
     *
     * @param pdfPath The [Uri] of the PDF file to delete.
     *                This URI should point to a valid, accessible file.
     *
     * @throws SecurityException If the application lacks the necessary permissions to
     *                         delete the file. This could happen if the URI points
     *                         to a file in a location that the app cannot access.
     *                         This exception is a RuntimeException, so you should
     *                         handle it.
     * @throws IllegalArgumentException If the provided Uri is invalid or does not represent a file.
     *                                 For example, a scheme not supported by the content resolver.
     *
     * @see Uri
     * @see android.content.ContentResolver
     *
     */
    suspend fun deleteScannedPdf(pdfPath: Uri)

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
