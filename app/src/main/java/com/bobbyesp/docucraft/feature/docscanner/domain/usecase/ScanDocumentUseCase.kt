package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.mlkit.domain.error.OperationFailure
import com.bobbyesp.docucraft.mlkit.domain.model.Document
import com.bobbyesp.docucraft.mlkit.domain.repository.DocumentScannerService
import com.bobbyesp.docucraft.core.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Use case for scanning a document. It orchestrates the scanning process using the
 * DocumentScannerRepository.
 */
class ScanDocumentUseCase(private val scannerRepository: DocumentScannerService) {
    /**
     * Executes the document scanning process.
     *
     * @param input The input image data (e.g., Bitmap, ImageProxy).
     * @return A Flow emitting the status of the scan (Loading -> Success/Error).
     */
    operator fun invoke(input: Any): Flow<Resource<Document>> = flow {
        emit(Resource.Loading())
        val result = scannerRepository.scanDocument(input)
        result.fold(
            onSuccess = { document -> emit(Resource.Success(document)) },
            onFailure = { error ->
                if (error is OperationFailure.ScanCancelled) {
                    emit(Resource.Idle())
                    return@fold
                }
                emit(Resource.Error(message = error.message ?: "Unknown error", error = error))
            },
        )
    }
}
