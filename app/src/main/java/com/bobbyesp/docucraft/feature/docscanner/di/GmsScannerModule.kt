package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.mlkit.domain.datsource.MlKitDataSource
import com.bobbyesp.docucraft.mlkit.data.datasource.MlKitDataSourceImpl
import com.bobbyesp.docucraft.feature.docscanner.data.local.repository.MlKitDocumentScannerService
import com.bobbyesp.docucraft.mlkit.domain.repository.DocumentScannerService
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import org.koin.dsl.module

val mlKitModule = module {
    // Data Source
    single<MlKitDataSource> { MlKitDataSourceImpl() }

    // Repository
    single<DocumentScannerService> { MlKitDocumentScannerService(get()) }
}

val gmsScannerModule = module {
    single<GmsDocumentScannerOptions> {
        GmsDocumentScannerOptions.Builder()
            .setScannerMode(SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true)
            .setResultFormats(RESULT_FORMAT_PDF)
            .build()
    }

    single<GmsDocumentScanner> { GmsDocumentScanning.getClient(get()) }
}
