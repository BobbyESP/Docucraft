package com.bobbyesp.docucraft.feature.pdfscanner.domain.repository

import android.net.Uri
import com.bobbyesp.docucraft.feature.pdfscanner.data.local.db.entity.ScannedPdfEntity
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import kotlinx.coroutines.flow.Flow

interface ScannedPdfRepository {

    /**
     * Retrieves a flow of all scanned PDFs available in the system.
     *
     * This function asynchronously fetches a list of `ScannedPdf` objects and emits them as a
     * stream of data through a `Flow`.
     *
     * The flow will emit a new list of `ScannedPdf` whenever the underlying data changes, for
     * example, when a new PDF is scanned or an existing PDF is deleted.
     *
     * **Note:** This function operates in a suspend context, meaning it can be safely called from
     * within a coroutine or another suspend function.
     *
     * @return A `Flow` that emits lists of `ScannedPdf` objects. Each emission represents the
     *   current state of all scanned PDFs.
     * @see ScannedPdf
     * @see Flow
     */
    suspend fun getAllScannedPdfsFlow(): Flow<List<ScannedPdf>>

    /**
     * Searches for PDF files based on a given query string in their names.
     *
     * This function simulates searching for PDF files by their names. It might interact
     * with a file system or a database in a real implementation. The function is
     * designed to be used within a coroutine context as it is marked as `suspend`.
     *
     * @param query The string to search for within the PDF file names. The search is
     *              case-insensitive and checks if the query is a substring of the file name.
     * @return A list of [ScannedPdf] objects matching the search query. Returns an empty
     *         list if no matches are found.
     *
     * @sample
     *  // Example usage (within a coroutine scope):
     *  launch {
     *      val results = searchPdfsByName("report")
     *      if (results.isNotEmpty()) {
     *          println("Found ${results.size} PDFs matching 'report':")
     *          results.forEach { println(it) }
     *      } else {
     *          println("No PDFs found matching 'report'.")
     *      }
     *  }
     *
     *  // Example with empty string
     *  launch {
     *       val results = searchPdfsByName("")
     *       println("Found ${results.size} PDFs matching empty String:")
     *       results.forEach { println(it) }
     *  }
     *
     *  // Example with non-existing name
     *  launch {
     *       val results = searchPdfsByName("nonExisting")
     *       println("Found ${results.size} PDFs matching nonExisting:")
     *       results.forEach { println(it) }
     *   }
     *
     * @throws Exception If any error occurs during the search process (e.g., file system issues).
     *                    In this simulated version, this never throws an exception.
     *
     * @see ScannedPdf
     */
    suspend fun searchPdfsByName(query: String): List<ScannedPdf>

    /**
     * Searches for PDF documents based on a description query.
     *
     * This function asynchronously searches for PDF documents whose descriptions match the provided query string.
     * The search is performed against a data source that is expected to contain PDF metadata, including descriptions.
     * The matching is case-insensitive and may include partial matches depending on the underlying search implementation.
     *
     * @param query The search query string. This string will be used to find PDF descriptions that match.
     *              It can be a single word or a phrase.
     * @return A list of [ScannedPdf] objects whose descriptions match the query. Returns an empty list if no matches are found or if an error occurs during the search.
     *
     * @throws Exception If an error occurs during the search operation (e.g., database access issues, network problems).
     */
    suspend fun searchPdfsByDescription(query: String): List<ScannedPdf>

    /**
     * Searches for scanned PDFs based on a query string, matching against either the title or the description of the PDFs.
     *
     * This function performs a case-insensitive search. It will return a list of ScannedPdf objects
     * where either the title or the description contains the provided query string.
     *
     * This is a suspend function, meaning it must be called within a coroutine or another suspend function.
     * This allows for non-blocking execution, particularly useful when dealing with potentially long-running
     * operations like database or network searches.
     *
     * @param query The search query string. The function will look for PDFs whose title or description contains this string.
     *              It handles empty queries by potentially returning all PDFs (implementation dependent).
     * @return A list of [ScannedPdf] objects that match the search query in either their title or description.
     *         Returns an empty list if no matches are found or if the underlying data source is empty.
     * @throws Exception if there is an error during the search operation, such as a database error or a network failure.
     *                   Specific exceptions that may be thrown depend on the underlying data access implementation.
     *
     * @see ScannedPdf
     */
    suspend fun searchPdfsByTitleOrDescription(query: String): List<ScannedPdf>

    /**
     * Retrieves a scanned PDF by its unique identifier.
     *
     * This function fetches a ScannedPdf object based on the provided `pdfId`. It is a suspending
     * function, meaning it can be safely called within a coroutine and will suspend execution until
     * the PDF is retrieved or an error occurs.
     *
     * @param pdfId The unique identifier of the scanned PDF to retrieve. Must not be null or empty.
     * @return The [ScannedPdf] object corresponding to the provided `pdfId`.
     * @throws IllegalArgumentException if the `pdfId` is null or empty.
     * @throws NoSuchElementException if no ScannedPdf with the given `pdfId` exists.
     * @throws Exception if any other error occurs during the retrieval process.
     */
    suspend fun getScannedPdfById(pdfId: String): ScannedPdf

    /**
     * Retrieves a ScannedPdf object from the specified path.
     *
     * This function asynchronously fetches the metadata and content of a PDF
     * file located at the provided URI path and constructs a `ScannedPdf`
     * object representing it.
     *
     * @param pdfPath The URI representing the path to the PDF file. This should
     *                be a valid content URI or file URI pointing to an existing PDF file.
     * @return A `ScannedPdf` object containing the metadata and content of the
     *         PDF file.
     * @throws IllegalArgumentException If the provided `pdfPath` is null or invalid,
     *                                  or if the file at the specified path is not a PDF.
     * @throws FileNotFoundException if the file specified by the path does not exist.
     * @throws Exception if any other error occurs during the process. For example if the
     *                  read operation is interrupted.
     *
     * @see ScannedPdf
     * @see Uri
     */
    suspend fun getScannedPdfByPath(pdfPath: Uri): ScannedPdf

    /**
     * Saves a scanned PDF to the persistent storage.
     *
     * This function takes a [ScannedPdfEntity] representing the scanned PDF and
     * performs the necessary operations to save it. This might include:
     *   - Writing the PDF data (likely a ByteArray or File path) to a designated
     *     directory.
     *   - Storing metadata about the PDF, such as its name, creation date,
     *     and any associated tags or notes, in a database or other structured
     *     storage.
     *   - Generating a unique identifier for the saved PDF.
     *   - Updating the UI to reflect the new saved PDF.
     *   - Handling any potential errors during the save operation.
     *
     * This is a suspending function, meaning it can safely perform long-running
     * operations (like file I/O) without blocking the main thread. It should be
     * called within a coroutine scope.
     *
     * @param scannedPdf The [ScannedPdfEntity] object containing the data and
     *                   metadata of the scanned PDF to be saved.
     * @throws Exception If any error occurs during the saving process, such as
     *                   file writing errors, database errors, or invalid data.
     */
    suspend fun savePdf(scannedPdf: ScannedPdfEntity)

    /**
     * Modifies the title and/or description of a PDF document.
     *
     * This function allows updating the title and description of a PDF document identified by its ID.
     * Either the title or the description, or both, can be modified in a single call.
     * If a parameter is null, that specific field will not be updated.
     *
     * @param pdfId The unique identifier of the PDF document to be modified. Must not be null or empty.
     * @param title The new title for the PDF document. If null, the title will not be updated.
     * @param description The new description for the PDF document. If null, the description will not be updated.
     *
     * @throws IllegalArgumentException if the `pdfId` is null or empty.
     * @throws FileNotFoundException if the file specified by the path does not exist.
     *
     * @sample
     * ```kotlin
     * // Example usage:
     * try {
     *     modifyTitleAndDescription("pdf123", "My New Title", "This is the updated description.")
     *     println("PDF title and description updated successfully.")
     * } catch (e: IllegalArgumentException) {
     *     println("Invalid PDF ID: ${e.message}")
     * } catch (e: SomeOtherException) {
     *      println("Database connection issue: ${e.message}")
     * } catch (e: AnotherException) {
     *      println("Error updating metadata: ${e.message}")
     * }
     *
     * try {
     *      modifyTitleAndDescription("pdf456", "Only Title Updated", null)
     *      println("PDF title updated successfully.")
     * } catch (e: Exception) {
     *      println("Error: ${e.message}")
     * }
     *
     * try {
     *      modifyTitleAndDescription("pdf789", null, "Only Description Updated")
     *      println("PDF description updated successfully.")
     * } catch (e: Exception){
     *      println("Error: ${e.message}")
     * }
     *
     * try{
     *    modifyTitleAndDescription("", "title", "desc") //This will throw an IllegalArgumentException
     * } catch (e: IllegalArgumentException){
     */
    suspend fun modifyTitleAndDescription(pdfId: String, title: String?, description: String?)

    /**
     * Deletes a PDF file located at the specified URI.
     *
     * This function attempts to delete the PDF file pointed to by the given `pdfPath`.
     * It's important to note that this function operates on a content URI and relies on
     * the content provider to manage the actual file deletion.
     *
     * @param pdfPath The URI representing the location of the PDF file to be deleted.
     *                This should be a content URI (e.g., "content://...").
     * @throws SecurityException If the application does not have the necessary
     *                          permissions to delete the file at the specified URI.
     * @throws Exception If any other error occurs during the deletion process.
     *
     * @sample
     * // Example usage:
     * val pdfUri: Uri = ... // Obtain the URI of the PDF to delete.
     * try {
     *     deletePdf(pdfUri)
     *     println("PDF deleted successfully!")
     * } catch (e: SecurityException) {
     *     println("Error: Insufficient permissions to delete the PDF.")
     * } catch (e: FileNotFoundException){
     *     println("Error: PDF file not found.")
     * } catch (e: Exception) {
     *     println("Error: Failed to delete PDF: ${e.message}")
     * }
     */
    suspend fun deletePdf(pdfPath: Uri)
}
