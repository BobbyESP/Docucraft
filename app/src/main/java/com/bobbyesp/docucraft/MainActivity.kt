package com.bobbyesp.docucraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import coil.imageLoader
import com.bobbyesp.docucraft.core.data.local.preferences.AppPreferences
import com.bobbyesp.docucraft.core.presentation.common.AppLocalSettingsProvider
import com.bobbyesp.docucraft.core.presentation.common.LocalNavController
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.HomeViewModel
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.widgets.ACTION_SCAN_PDF
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import org.koin.android.ext.android.inject
import org.koin.compose.KoinContext
import org.koin.core.component.KoinComponent

class MainActivity : ComponentActivity(), KoinComponent {

    private val appPreferences: AppPreferences by inject()
    private val homeViewModel by inject<HomeViewModel>()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        FileKit.init(this)

        if (intent?.action == ACTION_SCAN_PDF) {
            val scannerLauncher = registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                homeViewModel.onEvent(HomeViewModel.Event.HandlePdfScanningResult(result))
            }

            homeViewModel.onEvent(
                HomeViewModel.Event.ScanPdf(
                    activity = this,
                    listener = scannerLauncher
                )
            )
        }

        setContent {
            val navHostController = rememberNavController()
            val sonner = rememberToasterState()
            KoinContext {
                DocucraftTheme {
                    val windowSizeClass = calculateWindowSizeClass(this)

                    CompositionLocalProvider(LocalNavController provides navHostController) {
                        AppLocalSettingsProvider(
                            windowWidthSize = windowSizeClass.widthSizeClass,
                            sonner = sonner,
                            appPreferences = appPreferences,
                            imageLoader = imageLoader,
                        ) {
                            Navigator(navHostController = navHostController)
                            Toaster(
                                state = sonner,
                                richColors = true,
                                darkTheme =
                                    isSystemInDarkTheme(), // TODO: Change by the settings wrapper
                            )
                        }
                    }
                }
            }
        }
    }
}
