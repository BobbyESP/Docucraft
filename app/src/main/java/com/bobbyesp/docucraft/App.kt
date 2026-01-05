package com.bobbyesp.docucraft

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.bobbyesp.docucraft.core.di.appCoroutinesScope
import com.bobbyesp.docucraft.core.di.coreFunctionalitiesModule
import com.bobbyesp.docucraft.core.di.coreModule
import com.bobbyesp.docucraft.core.di.fileManagementModule
import com.bobbyesp.docucraft.feature.pdfscanner.di.gmsScannerModule
import com.bobbyesp.docucraft.feature.pdfscanner.di.pdfScannerDataModule
import com.bobbyesp.docucraft.feature.pdfscanner.di.pdfScannerViewModels
import com.bobbyesp.docucraft.feature.pdfscanner.di.scannedPdfsDatabaseModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(
                scannedPdfsDatabaseModule,
                pdfScannerDataModule,
                gmsScannerModule,
                pdfScannerViewModels,
                appCoroutinesScope,
                coreModule,
                coreFunctionalitiesModule,
                fileManagementModule,
            )
        }
        packageInfo = packageManager.run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
            else getPackageInfo(packageName, 0)
        }
    }

    companion object {
        lateinit var packageInfo: PackageInfo
        fun getAuthority(context: Context): String {
            return "${context.packageName}.fileprovider"
        }
    }
}
