package com.bobbyesp.docucraft

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Alignment
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.imageLoader
import com.bobbyesp.docucraft.core.data.local.preferences.AppPreferences
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.domain.repository.InAppNotificationsService
import com.bobbyesp.docucraft.core.presentation.common.AppLocalSettingsProvider
import com.bobbyesp.docucraft.core.presentation.common.LocalDarkTheme
import com.bobbyesp.docucraft.core.presentation.navigation.Route
import com.bobbyesp.docucraft.core.presentation.navigation.backstack.rememberTopLevelBackStack
import com.bobbyesp.docucraft.core.presentation.notifications.SonnerNotificationServiceImpl
import com.bobbyesp.docucraft.feature.docscanner.domain.ScannerManager
import com.bobbyesp.docucraft.feature.docscanner.domain.repository.ScannerRepository
import com.bobbyesp.docucraft.feature.docscanner.presentation.widgets.ACTION_SCAN_DOCUMENT
import com.dokar.sonner.Toaster
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent

class MainActivity : ComponentActivity() {

    private val appPreferences: AppPreferences by inject()
    private val inAppNotificationsService: InAppNotificationsService by inject()
    private val analyticsHelper: AnalyticsHelper by inject()

    private val scannerRepository: ScannerRepository by inject()
    private val scannerManager: ScannerManager by inject()
    private val scannerClient: GmsDocumentScanner by inject()

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        lifecycleScope.launch {
            scannerRepository.processResult(result)
                .onSuccess { rawScanResult ->
                    scannerManager.onScanResult(
                        Result.success(rawScanResult)
                    )
                }.onFailure { error ->
                    scannerManager.onScanResult(Result.failure(error))
                }
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        FileKit.init(this)
        val sonnerManager = inAppNotificationsService as SonnerNotificationServiceImpl

        handleIntent(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                scannerManager.scanRequest.collect {
                    launchScanner()
                }
            }
        }

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val rootBackStack = rememberTopLevelBackStack(startRoute = Route.Home)

            AppLocalSettingsProvider(
                windowWidthSize = windowSizeClass.widthSizeClass,
                inAppNotificationsService = inAppNotificationsService,
                appPreferences = appPreferences,
                imageLoader = imageLoader,
                analyticsHelper = analyticsHelper
            ) {
                Navigator(
                    rootBackStack = rootBackStack,
                )

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

    private fun launchScanner() {
        scannerClient.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            }
            .addOnFailureListener { e ->
                lifecycleScope.launch {
                    scannerManager.onScanResult(Result.failure(e))
                }
            }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_SCAN_DOCUMENT) {
            lifecycleScope.launch { scannerManager.requestScan() }
            intent.action = null
        }
    }
}
