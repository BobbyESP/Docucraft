package com.bobbyesp.docucraft.feature.docscanner.domain.exception

sealed class ScanSaveException(override val message: String): Exception(message) {
    class OutputFileNotCopied: ScanSaveException("An error occurred saving the file to the app's internal storage")
}