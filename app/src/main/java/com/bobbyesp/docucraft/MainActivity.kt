package com.bobbyesp.docucraft

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Alignment
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import coil.imageLoader
import com.bobbyesp.docucraft.core.data.local.preferences.AppPreferences
import com.bobbyesp.docucraft.mlkit.domain.repository.DocumentScannerService
import com.bobbyesp.docucraft.core.presentation.common.AppLocalSettingsProvider
import com.bobbyesp.docucraft.core.presentation.common.LocalDarkTheme
import com.bobbyesp.docucraft.core.presentation.common.Route
import com.bobbyesp.docucraft.core.presentation.navigation.TopLevelBackStack
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.pages.home.viewmodel.HomeViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.widgets.ACTION_SCAN_PDF
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent

class MainActivity : ComponentActivity(), KoinComponent {

    private val appPreferences: AppPreferences by inject()
    private val homeViewModel by inject<HomeViewModel>()
    private val documentScannerService by inject<DocumentScannerService>()
    private val topLevelBackStack by inject<TopLevelBackStack<Route>>()

    private lateinit var scannerLauncher: ActivityResultLauncher<IntentSenderRequest>

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        FileKit.init(this)

        scannerLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result
                ->
                homeViewModel.onAction(HomeUiAction.OnScanResultReceived(result))
            }

        handleIntent(intent)

        setContent {
            val sonner = rememberToasterState()

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
                    showCloseButton = true,
                    alignment = Alignment.TopCenter,
                    darkTheme = LocalDarkTheme.current.isDarkTheme(),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Fix for FileKit memory leak: Clear the static registry reference if it points to this
        // activity
        try {
            val fileKitClass = Class.forName("io.github.vinceglb.filekit.dialogs.FileKitDialog")
            val registryField = fileKitClass.getDeclaredField("_registry")
            registryField.isAccessible = true
            val currentRegistry = registryField.get(null)

            if (currentRegistry === this.activityResultRegistry) {
                Log.d("FileKit", "Clearing FileKit registry reference")
                registryField.set(null, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_SCAN_PDF) {
            documentScannerService.launchScanner(this, scannerLauncher)
            intent.action = null
        }
    }
}
