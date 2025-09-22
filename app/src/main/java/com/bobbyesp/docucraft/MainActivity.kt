package com.bobbyesp.docucraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import coil.imageLoader
import com.bobbyesp.docucraft.core.data.local.preferences.AppPreferences
import com.bobbyesp.docucraft.core.presentation.common.AppLocalSettingsProvider
import com.bobbyesp.docucraft.core.presentation.common.Route
import com.bobbyesp.docucraft.core.presentation.navigation.TopLevelBackStack
import com.bobbyesp.docucraft.core.presentation.theme.DocucraftTheme
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home.viewmodel.HomeViewModel
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.widgets.ACTION_SCAN_PDF
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent

class MainActivity : ComponentActivity(), KoinComponent {

    private val appPreferences: AppPreferences by inject()
    private val homeViewModel by inject<HomeViewModel>()
    private val topLevelBackStack by inject<TopLevelBackStack<Route>>()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        FileKit.init(this)

        if (intent?.action == ACTION_SCAN_PDF) {
            val scannerLauncher =
                registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                    result ->
                    homeViewModel.onEvent(HomeViewModel.Event.HandlePdfScanningResult(result))
                }

            homeViewModel.onEvent(
                HomeViewModel.Event.ScanPdf(activity = this, listener = scannerLauncher)
            )
        }

        setContent {
            val sonner = rememberToasterState()
            DocucraftTheme {
                val windowSizeClass = calculateWindowSizeClass(this)

                AppLocalSettingsProvider(
                    windowWidthSize = windowSizeClass.widthSizeClass,
                    sonner = sonner,
                    appPreferences = appPreferences,
                    imageLoader = imageLoader,
                ) {
                    Navigator(topLevelBackStack = topLevelBackStack)
                    Toaster(
                        state = sonner,
                        richColors = true,
                        darkTheme = isSystemInDarkTheme(), // TODO: Change by the settings wrapper
                    )
                }
            }
        }
    }
}
