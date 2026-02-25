package com.bobbyesp.docucraft.feature.docscanner.domain.usecase

import com.bobbyesp.docucraft.core.util.state.ResourceState
import com.bobbyesp.docucraft.mlkit.domain.exception.OperationFailure
import com.bobbyesp.docucraft.mlkit.domain.model.Document
import com.bobbyesp.docucraft.mlkit.domain.repository.DocumentScannerService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Use case for scanning a document. It orchestrates the scanning process using the
 * DocumentScannerRepository.
 */
class ProcessScanningResultUseCase(private val scannerRepository: DocumentScannerService) {
    /**
     * Executes the document scanning process.
     *
     * @param input The input image data (e.g., Bitmap, ImageProxy).
     * @return A Flow emitting the status of the scan (Loading -> Success/Error).
     */
    operator fun invoke(input: Any): Flow<ResourceState<Document>> = flow {
        emit(ResourceState.Loading())
        val result = scannerRepository.processResult(input)
        result.fold(
            onSuccess = { document -> emit(ResourceState.Success(document)) },
            onFailure = { error ->
                if (error is OperationFailure.ScanCancelled) {
                    return@fold
                }
                emit(ResourceState.Error(errorMessage = error.message ?: "Unknown error"))
            },
        )
    }
}
