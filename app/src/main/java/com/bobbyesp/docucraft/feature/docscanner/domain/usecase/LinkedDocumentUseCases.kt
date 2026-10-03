/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.feature.docscanner.domain.model.Document
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.DocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LinkedDocumentFacts
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.LinkedDocumentsRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.NewLinkedDocument
import com.bobbyesp.docucraft.feature.docscanner.domain.storage.ExternalDocumentAccess
import com.bobbyesp.scanner.ContentRef

/**
 * Makes a PDF of another app, opened here, a document of the catalogue: one it only refers to, so
 * that it can be found again in Recents. Opening the same location again is the same document.
 *
 * The app keeps a reference and never a copy: its file stays where it is, the other app's to change
 * or delete. Keeping it is something the user asks for (`SaveLinkedToLibraryUseCase`).
 */
class RegisterLinkedDocumentUseCase(
    private val access: ExternalDocumentAccess,
    private val linked: LinkedDocumentsRepository,
) {
    /**
     * @param displayName What the other app calls it, with or without its extension.
     * @return The document's uuid.
     */
    suspend operator fun invoke(location: ContentRef, displayName: String): String {
        // First, while the other app's loan is certain to be standing.
        val kept = access.keep(location)

        val registration =
            linked.register(
                NewLinkedDocument(
                    location = location,
                    originalName = displayName.withoutPdfExtension(),
                    hasPersistedPermission = kept,
                ),
                limit = MAX_LINKED_DOCUMENTS,
            )

        // The platform keeps a limited number of permissions for an app. One held for a document
        // that is no longer referred to is one fewer for the next.
        registration.forgotten.forEach(access::release)

        return registration.uuid
    }

    private fun String.withoutPdfExtension(): String =
        if (endsWith(PDF_EXTENSION, ignoreCase = true)) dropLast(PDF_EXTENSION.length) else this

    companion object {
        /**
         * How many documents of other apps are referred to at once. They are only ever listed in
         * Recents, which shows a handful: without a limit they would pile up unseen, each holding a
         * permission.
         */
        const val MAX_LINKED_DOCUMENTS = 50

        private const val PDF_EXTENSION = ".pdf"
    }
}

/**
 * Takes a document of another app out of Recents. Only the reference goes: the file is not ours.
 */
class ForgetLinkedDocumentUseCase(
    private val linked: LinkedDocumentsRepository,
    private val access: ExternalDocumentAccess,
) {
    suspend operator fun invoke(documentUuid: String) {
        linked.forget(documentUuid)?.let(access::release)
    }
}

/**
 * Notes what a linked document turned out to be, once it has been opened: its size, its pages, a
 * hash of its content and whether it is protected. Its file is another app's and can change between
 * two openings, so this is done on each.
 *
 * It reads the whole file, which is why it is not part of registering: the document is shown first.
 * That is also the moment it can be told whether the library already has this content, so it is
 * answered here rather than by reading the file a second time.
 */
class DescribeLinkedDocumentUseCase(
    private val documents: DocumentsRepository,
    private val linked: LinkedDocumentsRepository,
    private val access: ExternalDocumentAccess,
) {
    /**
     * @param pageCount Its pages, when it could be opened.
     * @param isProtected Whether it asked for a password.
     * @return The uuid of a document of the library with the same content, or `null`: there is
     *   none, the file could not be read, or this is not a linked document.
     */
    suspend operator fun invoke(
        documentUuid: String,
        pageCount: Int?,
        isProtected: Boolean,
    ): String? {
        val document =
            runCatching { documents.getDocument(documentUuid) }.getOrNull() as? Document.Linked
                ?: return null

        val file = access.measure(document.location)
        linked.describe(
            documentUuid,
            LinkedDocumentFacts(
                sizeBytes = file?.sizeBytes,
                contentHash = file?.contentHash,
                pageCount = pageCount,
                isProtected = isProtected,
            ),
        )
        return file?.let { documents.findInLibrary(it.contentHash)?.uuid }
    }
}
