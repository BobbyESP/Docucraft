/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.feature.docscanner.domain.ScanRequestBus
import com.bobbyesp.scanner.DocumentScanner
import com.bobbyesp.scanner.mlkit.ActivityResultHost
import com.bobbyesp.scanner.mlkit.ActivityResultHostImpl
import com.bobbyesp.scanner.mlkit.MlKitDocumentScanner
import org.koin.dsl.module

val documentScannerModule = module {
    // The Activity lends itself here; see MainActivity.
    single { ActivityResultHostImpl() }
    single<ActivityResultHost> { get<ActivityResultHostImpl>() }

    // ── The only place the app names a scanning engine ──
    single<DocumentScanner> { MlKitDocumentScanner(host = get()) }

    single { ScanRequestBus() }
}
