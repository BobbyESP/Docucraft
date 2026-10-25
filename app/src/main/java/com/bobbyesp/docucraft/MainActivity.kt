/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft

import android.content.Intent
import android.os.Bundle
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
import com.bobbyesp.docucraft.core.ui.MainActivityUiState
import com.bobbyesp.docucraft.core.ui.MainViewModel
import com.bobbyesp.docucraft.core.ui.common.AppLocalSettingsProvider
import com.bobbyesp.docucraft.core.ui.common.LocalDarkTheme
import com.bobbyesp.docucraft.core.ui.notifications.SonnerNotificationServiceImpl
import com.bobbyesp.docucraft.feature.home.domain.ScanRequestBus
import com.bobbyesp.docucraft.feature.widgets.presentation.ACTION_OPEN_DOCUMENT
import com.bobbyesp.docucraft.feature.widgets.presentation.ACTION_SCAN_DOCUMENT
import com.bobbyesp.docucraft.feature.widgets.presentation.ACTION_SEARCH_DOCUMENTS
import com.bobbyesp.docucraft.feature.widgets.presentation.EXTRA_DOCUMENT_UUID
import com.bobbyesp.docucraft.navigation.DocucraftApp
import com.bobbyesp.docucraft.requests.LibraryEntryRequest
import com.bobbyesp.docucraft.requests.LibraryEntryRequests
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
    private val entryRequests: LibraryEntryRequests by inject()

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
        // Rides in the Activity's own Bundle, which is the same thing that preserves the result
        // registry's pending request, so the two survive — or do not — together.
        resultHost.restorePendingLaunch(savedInstanceState?.getBoolean(KEY_PENDING_LAUNCH) == true)

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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_PENDING_LAUNCH, resultHost.hasPendingLaunch)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        resultHost.detach(this)
    }

    /**
     * What a home-screen widget asks for. Each becomes a standing request, because this runs before
     * anything that could act on it is composed.
     */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SCAN_DOCUMENT -> scanRequests.request()
            ACTION_SEARCH_DOCUMENTS -> entryRequests.request(LibraryEntryRequest.Search)
            ACTION_OPEN_DOCUMENT ->
                intent
                    .getStringExtra(EXTRA_DOCUMENT_UUID)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { entryRequests.request(LibraryEntryRequest.OpenDocument(it)) }

            else -> return
        }
        // The Activity keeps its Intent, and would be asked the same thing again each time it is
        // recreated.
        intent.action = null
    }

    private companion object {
        const val KEY_PENDING_LAUNCH = "pending_activity_result_launch"
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
