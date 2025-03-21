package com.bobbyesp.docucraft.feature.pdfscanner.di

import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import org.koin.dsl.module

val gmsScannerModule = module {
    single<GmsDocumentScannerOptions> {
        GmsDocumentScannerOptions.Builder().setScannerMode(SCANNER_MODE_FULL)
            .setGalleryImportAllowed(true).setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
            .build()
    }

    single<GmsDocumentScanner> {
        GmsDocumentScanning.getClient(get())
    }
}