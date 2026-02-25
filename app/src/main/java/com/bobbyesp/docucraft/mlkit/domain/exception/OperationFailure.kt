package com.bobbyesp.docucraft.mlkit.domain.exception

sealed class OperationFailure(override val message: String) : Exception(message) {
    class ScanCancelled(message: String = "Scan was cancelled by the user") : OperationFailure(message)

    class ProcessingError(message: String = "Failed to process the scanned document") :
        OperationFailure(message)

    class Unknown(message: String = "An unknown error occurred") : OperationFailure(message)
}