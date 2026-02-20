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
import com.bobbyesp.docucraft.core.data.local.repository.InAppNotificationServiceImpl
import com.bobbyesp.docucraft.core.domain.repository.InAppNotificationsService
import com.bobbyesp.docucraft.mlkit.domain.repository.DocumentScannerService
import com.bobbyesp.docucraft.core.presentation.common.AppLocalSettingsProvider
import com.bobbyesp.docucraft.core.presentation.common.LocalDarkTheme
import com.bobbyesp.docucraft.core.presentation.common.Route
import com.bobbyesp.docucraft.core.presentation.navigation.rememberTopLevelBackStack
import com.bobbyesp.docucraft.feature.docscanner.presentation.contract.HomeUiAction
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.viewmodel.HomeViewModel
import com.bobbyesp.docucraft.feature.docscanner.presentation.util.DocumentScannerLauncher
import com.bobbyesp.docucraft.feature.docscanner.presentation.widgets.ACTION_SCAN_DOCUMENT
import com.dokar.sonner.Toaster
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent

class MainActivity : ComponentActivity(), KoinComponent {

    private val appPreferences: AppPreferences by inject()
    private val homeViewModel by inject<HomeViewModel>()
    private val documentScannerService by inject<DocumentScannerService>()
    private val inAppNotificationsService by inject<InAppNotificationsService>()

    private lateinit var scannerLauncher: ActivityResultLauncher<IntentSenderRequest>

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        FileKit.init(this)
        val sonnerManager = inAppNotificationsService as InAppNotificationServiceImpl

        scannerLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result
                ->
                homeViewModel.onAction(HomeUiAction.OnScanResultReceived(result))
            }

        handleIntent(intent)

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val rootBackStack = rememberTopLevelBackStack(startRoute = Route.Home)

            AppLocalSettingsProvider(
                windowWidthSize = windowSizeClass.widthSizeClass,
                sonner = sonnerManager.sonnerState,
                appPreferences = appPreferences,
                imageLoader = imageLoader,
            ) {
                Navigator(rootBackStack = rootBackStack)

                Toaster(
                    state = sonnerManager.sonnerState,
                    richColors = true,
                    showCloseButton = true,
                    alignment = Alignment.TopCenter,
                    darkTheme =
                        LocalDarkTheme.current
                            .isDarkTheme(),
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
        if (intent?.action == ACTION_SCAN_DOCUMENT) {
            DocumentScannerLauncher.launch(this, scannerLauncher)
            intent.action = null
        }
    }
}
