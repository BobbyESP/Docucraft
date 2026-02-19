package com.bobbyesp.docucraft

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.bobbyesp.docucraft.core.di.commonModule
import com.bobbyesp.docucraft.core.di.fileManagementModule
import com.bobbyesp.docucraft.feature.docscanner.di.documentScannerDataModule
import com.bobbyesp.docucraft.feature.docscanner.di.documentScannerViewModels
import com.bobbyesp.docucraft.feature.docscanner.di.gmsScannerModule
import com.bobbyesp.docucraft.feature.docscanner.di.mlKitModule
import com.bobbyesp.docucraft.feature.docscanner.di.scannedDocumentsDatabaseModule
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
                scannedDocumentsDatabaseModule,
                documentScannerDataModule,
                gmsScannerModule,
                documentScannerViewModels,
                mlKitModule,
                commonModule,
                fileManagementModule,
            )
        }
        packageInfo =
            packageManager.run {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
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
