package com.bobbyesp.docucraft.feature.docscanner.domain.exception

sealed class DocumentExportFailure(override val message: String): Exception(message) {
    class Cancelled: DocumentExportFailure("Export cancelled by user")
    class Unknown: DocumentExportFailure("An unknown error occurred")
}