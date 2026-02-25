package com.bobbyesp.docucraft.feature.docscanner.domain.exception

sealed class ScanSavingException(override val message: String): Exception(message) {
    class OutputFileNotCopied: ScanSavingException("An error occurred saving the file to the app's internal storage")
}