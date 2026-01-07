package com.bobbyesp.docucraft.core.data.datasource.impl

import com.bobbyesp.docucraft.core.data.datasource.MlKitDataSource
import com.bobbyesp.docucraft.core.data.mapper.MlKitMapper
import com.bobbyesp.docucraft.core.domain.exception.ScannerException
import com.bobbyesp.docucraft.core.domain.model.ScannedDocument
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of the ML Kit Data Source. Handles the direct interaction with ML Kit
 * classes.
 */
class MlKitDataSourceImpl : MlKitDataSource {

    override suspend fun processScanningResult(result: Any): ScannedDocument {
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
