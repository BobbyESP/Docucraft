/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.domain.exception

sealed class ScanSaveException(override val message: String) : Exception(message) {

    /** The scan produced no document to save. */
    class NothingToSave : ScanSaveException("The scan produced no document to save")

    class OutputFileNotCopied :
        ScanSaveException("An error occurred saving the file to the app's internal storage")

    /** The document was copied but came out empty, so there is nothing worth cataloguing. */
    class OutputFileEmpty : ScanSaveException("The saved file is empty")

    /**
     * The scanner did not say how many pages it captured, and the file it produced cannot be opened
     * to count them. A document nobody can open is not worth cataloguing either.
     */
    class UnreadableDocument : ScanSaveException("The saved file cannot be read as a document")
}
