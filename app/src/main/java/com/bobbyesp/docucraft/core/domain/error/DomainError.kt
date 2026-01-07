package com.bobbyesp.docucraft.core.domain.error

sealed class DomainError(override val message: String) : Exception(message) {
    class ScanCancelled(message: String = "Scan was cancelled by the user") : DomainError(message)

    class ProcessingError(message: String = "Failed to process the scanned document") :
        DomainError(message)

    class UnknownError(message: String = "An unknown error occurred") : DomainError(message)
}
