package com.bobbyesp.docucraft.mlkit.data.datasource

import com.bobbyesp.docucraft.mlkit.domain.exception.ScannerException
import com.bobbyesp.docucraft.mlkit.domain.model.Document
import com.bobbyesp.docucraft.mlkit.data.mapper.MlKitMapper
import com.bobbyesp.docucraft.mlkit.domain.datsource.MlKitDataSource
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of the ML Kit Data Source. Handles the direct interaction with ML Kit
 * classes.
 */
class MlKitDataSourceImpl : MlKitDataSource {

    override suspend fun processDocumentScanResult(result: Any): Document {
        return withContext(Dispatchers.Default) {
            if (result !is GmsDocumentScanningResult) {
                throw ScannerException(
                    "Expected GmsDocumentScanningResult but received ${result::class.java.simpleName}"
                )
            }
            // Mapping can be computationally expensive if we process many pages, so we use Default
            // dispatcher
            MlKitMapper.mapToDomain(result)
        }
    }
}