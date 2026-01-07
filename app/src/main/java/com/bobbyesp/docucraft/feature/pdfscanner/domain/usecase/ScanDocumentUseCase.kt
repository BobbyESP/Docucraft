package com.bobbyesp.docucraft.feature.pdfscanner.domain.usecase

import com.bobbyesp.docucraft.core.domain.error.DomainError
import com.bobbyesp.docucraft.core.domain.model.ScannedDocument
import com.bobbyesp.docucraft.core.domain.repository.DocumentScannerRepository
import com.bobbyesp.docucraft.core.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Use case for scanning a document.
 * It orchestrates the scanning process using the DocumentScannerRepository.
 */
class ScanDocumentUseCase(
    private val scannerRepository: DocumentScannerRepository
) {
    /**
     * Executes the document scanning process.
     *
     * @param input The input image data (e.g., Bitmap, ImageProxy).
     * @return A Flow emitting the status of the scan (Loading -> Success/Error).
     */
    operator fun invoke(input: Any): Flow<Resource<ScannedDocument>> = flow {
        emit(Resource.Loading())
        val result = scannerRepository.scanDocument(input)
        result.fold(
            onSuccess = { document ->
                emit(Resource.Success(document))
            },
            onFailure = { error ->
                if(error is DomainError.ScanCancelled) {
                    emit(Resource.Idle())
                    return@fold
                }
                emit(Resource.Error(message = error.message ?: "Unknown error", error = error))
            }
        )
    }
}
