package com.bobbyesp.docucraft.feature.docscanner.domain.exception

sealed class ScannerException(override val message: String?) : Exception(message) {
    class ScanCancelled(message: String? = "Scan was cancelled by the user") : ScannerException(message)
    class ScanFailed(message: String?) : ScannerException(message)
    class Unknown(message: String? = "An unknown error occurred") : ScannerException(message)
}