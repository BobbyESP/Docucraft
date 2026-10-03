/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.bobbyesp.docucraft.core.di.analyticsModule
import com.bobbyesp.docucraft.core.di.commonModule
import com.bobbyesp.docucraft.core.di.notificationsServiceModule
import com.bobbyesp.docucraft.core.di.preferencesModule
import com.bobbyesp.docucraft.feature.docscanner.di.documentScannerDataModule
import com.bobbyesp.docucraft.feature.docscanner.di.documentScannerModule
import com.bobbyesp.docucraft.feature.docscanner.di.documentScannerViewModels
import com.bobbyesp.docucraft.feature.docscanner.di.scannedDocumentsDatabaseModule
import com.bobbyesp.docucraft.feature.docscanner.domain.usecase.ResumeTextIndexingUseCase
import com.bobbyesp.docucraft.feature.pdfviewer.di.pageContentModule
import com.bobbyesp.docucraft.feature.pdfviewer.di.pdfViewerModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.qualifier.named

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(
                commonModule,
                preferencesModule,
                notificationsServiceModule,
                scannedDocumentsDatabaseModule,
                documentScannerDataModule,
                documentScannerModule,
                documentScannerViewModels,
                pdfViewerModule,
                pageContentModule,
                analyticsModule,
            )
        }
        // Reading the text of the documents is picked up where it was left: a library just brought
        // over from an older catalogue, or a document saved as the app was closed. Never in the way
        // of starting: a failure here only means nothing is queued this time.
        get<CoroutineScope>(named("AppMainSupervisedScope")).launch {
            runCatching { get<ResumeTextIndexingUseCase>()() }
        }

        packageInfo = packageManager.run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            else getPackageInfo(packageName, 0)
        }
    }

    /**
     * The image loader every image in the app is loaded with: the one the graph configures. Coil
     * asks for it here the first time an image is requested. Without this the app ran on Coil's
     * default loader, and the configured one, with its caches, was built for nobody.
     */
    override fun newImageLoader(): ImageLoader = get()

    companion object {
        lateinit var packageInfo: PackageInfo

        fun getAuthority(context: Context): String {
            return "${context.packageName}.fileprovider"
        }
    }
}
