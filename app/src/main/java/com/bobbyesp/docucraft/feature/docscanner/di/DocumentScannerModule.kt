/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.docscanner.di

import com.bobbyesp.docucraft.core.presentation.activityresult.ActivityResultHost
import com.bobbyesp.docucraft.core.presentation.activityresult.ActivityResultHostImpl
import com.bobbyesp.docucraft.feature.docscanner.data.scanner.MlKitDocumentScanner
import com.bobbyesp.docucraft.feature.docscanner.domain.scanner.DocumentScanner
import com.bobbyesp.docucraft.feature.docscanner.domain.scanner.ScanRequestBus
import org.koin.dsl.module

val documentScannerModule = module {
    // The Activity lends itself here; see MainActivity.
    single { ActivityResultHostImpl() }
    single<ActivityResultHost> { get<ActivityResultHostImpl>() }

    // ── The only place the app names a scanning engine ──
    single<DocumentScanner> { MlKitDocumentScanner(host = get()) }

    single { ScanRequestBus() }
}
