package com.bobbyesp.docucraft

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.bobbyesp.docucraft.feature.pdfscanner.di.scannedPdfsDatabaseModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin

class App: Application() {
    override fun onCreate() {
        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(scannedPdfsDatabaseModule)
        }
        packageInfo = packageManager.run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getPackageInfo(
                packageName, PackageManager.PackageInfoFlags.of(0)
            ) else
                getPackageInfo(packageName, 0)
        }
        super.onCreate()
    }

    companion object {
        lateinit var packageInfo: PackageInfo
    }
}