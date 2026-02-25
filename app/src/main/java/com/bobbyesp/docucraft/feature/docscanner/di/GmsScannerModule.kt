package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.feature.docscanner.data.repository.MlKitScannerRepository
import com.bobbyesp.docucraft.feature.docscanner.domain.ScannerManager
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.ScannerRepository
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import org.koin.dsl.module

val mlKitModule = module {
    single<ScannerRepository> { MlKitScannerRepository() }
}

val gmsScannerModule = module {
    single<GmsDocumentScannerOptions> {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(Integer.MAX_VALUE)
            .setResultFormats(
                RESULT_FORMAT_PDF
            )
            .setScannerMode(SCANNER_MODE_FULL)
            .build()
    }

    single<GmsDocumentScanner> { GmsDocumentScanning.getClient(get()) }

    single<ScannerManager> { ScannerManager() }
}
