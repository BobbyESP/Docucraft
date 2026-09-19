/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.imageLoader
import com.bobbyesp.docucraft.core.domain.model.ThemeConfig
import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.domain.repository.InAppNotificationsService
import com.bobbyesp.docucraft.core.presentation.MainActivityUiState
import com.bobbyesp.docucraft.core.presentation.MainViewModel
import com.bobbyesp.docucraft.core.presentation.common.AppLocalSettingsProvider
import com.bobbyesp.docucraft.core.presentation.common.LocalDarkTheme
import com.bobbyesp.docucraft.core.presentation.navigation.DocucraftApp
import com.bobbyesp.docucraft.core.presentation.notifications.SonnerNotificationServiceImpl
import com.bobbyesp.docucraft.feature.docscanner.domain.ScanRequestBus
import com.bobbyesp.docucraft.feature.docscanner.presentation.widgets.ACTION_SCAN_DOCUMENT
import com.bobbyesp.scanner.mlkit.ActivityResultHostImpl
import com.dokar.sonner.Toaster
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent

class MainActivity : ComponentActivity(), KoinComponent {

    private val settingsRepository: SettingsRepository by inject()
    private val inAppNotificationsService: InAppNotificationsService by inject()
    private val analyticsHelper: AnalyticsHelper by inject()
    private val mainViewModel: MainViewModel by viewModel()

    private val scanRequests: ScanRequestBus by inject()

    /**
     * Registered here because only an Activity can, then lent to whoever needs it. This Activity
     * does not know, and must not know, what is being launched through it.
     */
    private val resultHost: ActivityResultHostImpl by inject()

    private val intentSenderLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            resultHost.deliver(result)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashscreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        var uiState: MainActivityUiState by mutableStateOf(MainActivityUiState.Loading)

        // Keep the splash screen on-screen until the settings are loaded
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.uiState.collect { state ->
                    uiState = state

                    // Reactively apply system bar theme overrides based on user preferences
                    if (state is MainActivityUiState.Success) {
                        val isDark =
                            state.userPreferences.themeConfig.shouldUseDarkTheme(this@MainActivity)
                        enableEdgeToEdge(
                            statusBarStyle =
                                if (isDark) {
                                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                                } else {
                                    SystemBarStyle.light(
                                        android.graphics.Color.TRANSPARENT,
                                        android.graphics.Color.TRANSPARENT,
                                    )
                                },
                            navigationBarStyle =
                                if (isDark) {
                                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                                } else {
                                    SystemBarStyle.light(
                                        android.graphics.Color.TRANSPARENT,
                                        android.graphics.Color.TRANSPARENT,
                                    )
                                },
                        )
                    }
                }
            }
        }

        splashscreen.setKeepOnScreenCondition { uiState is MainActivityUiState.Loading }

        FileKit.init(this)
        val sonnerManager = inAppNotificationsService as SonnerNotificationServiceImpl

        resultHost.attach(this, intentSenderLauncher)

        handleIntent(intent)

        setContent {
            val state = uiState
            if (state is MainActivityUiState.Success) {
                AppLocalSettingsProvider(
                    inAppNotificationsService = inAppNotificationsService,
                    imageLoader = imageLoader,
                    settingsRepository = settingsRepository,
                    userPreferences = state.userPreferences,
                    analyticsHelper = analyticsHelper,
                ) {
                    DocucraftApp()

                    Toaster(
                        state = sonnerManager.sonnerState,
                        richColors = true,
                        showCloseButton = true,
                        alignment = Alignment.TopCenter,
                        darkTheme = LocalDarkTheme.current,
                    )
                }
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
        resultHost.detach(this)

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
            lifecycleScope.launch { scanRequests.request() }
            intent.action = null
        }
    }

    private fun ThemeConfig.shouldUseDarkTheme(context: android.content.Context): Boolean =
        when (this) {
            ThemeConfig.FOLLOW_SYSTEM -> {
                val uiMode =
                    context.resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            ThemeConfig.LIGHT -> false
            ThemeConfig.DARK -> true
        }
}
