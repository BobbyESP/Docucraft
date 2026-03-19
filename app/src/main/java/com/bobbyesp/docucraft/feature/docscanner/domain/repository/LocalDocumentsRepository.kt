package com.bobbyesp.docucraft.feature.docscanner.domain.repository

import android.net.Uri
import com.bobbyesp.docucraft.feature.docscanner.data.db.entity.ScannedDocumentEntity
import com.bobbyesp.docucraft.feature.docscanner.domain.model.ScannedDocument
import kotlinx.coroutines.flow.Flow

interface LocalDocumentsRepository {

    /**
     * Retrieves a reactive stream of all scanned documents currently available in the system.
     *
     * This function subscribes to the underlying data source (e.g., Room database) and emits
     * a new list of [ScannedDocument] objects whenever the data set changes. This includes events
     * such as adding a new scan, deleting a document, or updating a document's metadata.
     *
     * The returned [Flow] is infinite and will continue to emit updates until the consumer
     * cancels the collection.
     *
     * @return A [Flow] emitting the complete list of [ScannedDocument]s representing the current state.
     */
    suspend fun observeDocuments(): Flow<List<ScannedDocument>>

    /**
     * Performs a unified search across all text fields of the scanned documents.
     *
     * This function simplifies search operations by applying the [query] string against multiple
     * attributes of the PDF documents simultaneously. Typically, this includes the **file name**,
     * **user-defined title**, and **description**.
     *
     * The search is expected to be case-insensitive and allow partial matches (substrings).
     * This centralization allows the UI to expose a single search bar without requiring the user
     * to specify which field to filter by.
     *
     * @param query The text string to search for.
     * @return A list of [ScannedDocument] objects where the query matches at least one of the searchable fields.
     *         Returns an empty list if no matches are found.
     */
    suspend fun searchDocuments(query: String): List<ScannedDocument>

    /**
     * Retrieves a single scanned PDF by its unique identifier.
     *
     * Performs a lookup in the persistence layer to find the document with the matching [uuid].
     * This is useful for opening details screens or performing operations on a specific item.
     *
     * @param uuid The unique identifier string of the PDF to retrieve.
     * @return The [ScannedDocument] domain object corresponding to the ID.
     * @throws NoSuchElementException If no document is found with the provided ID.
     */
    suspend fun getDocument(uuid: String): ScannedDocument

    /**
     * Retrieves a ScannedPdf object based on its file location URI.
     *
     * This is useful when the application receives an intent with a file URI or needs to
     * reconcile a file on disk with its database entry. It may involve reading file metadata
     * from the [path] if the entry is not fully cached.
     *
     * @param path The [Uri] pointing to the PDF file location.
     * @return A [ScannedDocument] object representing the file at the given path.
     * @throws IllegalArgumentException If the URI is invalid or the file does not exist.
     */
    suspend fun getDocument(path: Uri): ScannedDocument

    /**
     * Persists a new scanned PDF entity to storage.
     *
     * This function handles the creation of a new record in the database. It interprets the
     * provided [ScannedDocumentEntity], which contains the raw data structure (path, dimensions,
     * initial metadata), and commits it to the persistent store.
     *
     * Operations may include:
     * - Inserting the record into the Room database.
     * - verifying the physical file existence.
     * - Triggering the [observeDocuments] flow to emit the new list.
     *
     * @param scannedDocument The entity object containing the data to be saved.
     */
    suspend fun saveDocument(scannedDocument: ScannedDocumentEntity)

    /**
     * Updates specific metadata fields (title and/or description) of an existing document.
     *
     * This function offers granular control over updates. Passing `null` for a parameter indicates
     * that the current value of that field should remain unchanged. This allows for partial updates
     * (e.g., renaming the file without clearing the description).
     *
     * @param id The unique identifier of the document to update.
     * @param title The new title to set. If `null`, the existing title is preserved.
     * @param description The new description to set. If `null`, the existing description is preserved.
     * @throws IllegalArgumentException If the provided [id] does not exist.
     */
    suspend fun modifyFields(id: String, title: String?, description: String?)

    /**
     * Permanently removes a document from the system.
     *
     * This operation performs a cleanup that involves:
     * 1. Removing the record from the local database.
     * 2. (Optional depending on implementation) Deleting the actual physical file from the device storage
     *    referenced by [pdfPath].
     *
     * @param path The [Uri] of the document to be deleted.
     * @throws SecurityException If the app lacks permissions to delete the physical file.
     */
    suspend fun deleteDocument(path: Uri)
}