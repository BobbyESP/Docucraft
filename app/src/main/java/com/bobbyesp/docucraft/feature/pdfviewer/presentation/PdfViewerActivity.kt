/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.docucraft.feature.pdfviewer.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.imageLoader
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.domain.repository.InAppNotificationsService
import com.bobbyesp.docucraft.core.presentation.MainActivityUiState
import com.bobbyesp.docucraft.core.presentation.MainViewModel
import com.bobbyesp.docucraft.core.presentation.common.AppLocalSettingsProvider
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens.PdfViewerScreen
import com.bobbyesp.docucraft.feature.shared.domain.BasicDocument
import java.util.UUID
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent

/**
 * Standalone activity that lets Docucraft act as a system PDF viewer for documents outside the app.
 *
 * Registered with `ACTION_VIEW` / `ACTION_SEND` intent-filters for `application/pdf`, it wraps the
 * incoming URI into a synthetic [BasicDocument] and reuses [PdfViewerScreen]. It is intentionally
 * separate from [com.bobbyesp.docucraft.MainActivity] so the main app's single back stack stays
 * untouched — back here simply finishes and returns to the calling app.
 */
class PdfViewerActivity : ComponentActivity(), KoinComponent {

    private val settingsRepository: SettingsRepository by inject()
    private val inAppNotificationsService: InAppNotificationsService by inject()
    private val analyticsHelper: AnalyticsHelper by inject()
    private val mainViewModel: MainViewModel by viewModel()

    private var document by mutableStateOf<BasicDocument?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashscreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val resolved = resolveDocument(intent)
        if (resolved == null) {
            Toast.makeText(this, R.string.cannot_open_pdf, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        document = resolved

        var uiState: MainActivityUiState by mutableStateOf(MainActivityUiState.Loading)
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.uiState.collect { uiState = it }
            }
        }
        splashscreen.setKeepOnScreenCondition { uiState is MainActivityUiState.Loading }

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val state = uiState
            val doc = document
            if (state is MainActivityUiState.Success && doc != null) {
                AppLocalSettingsProvider(
                    windowWidthSize = windowSizeClass.widthSizeClass,
                    inAppNotificationsService = inAppNotificationsService,
                    imageLoader = imageLoader,
                    settingsRepository = settingsRepository,
                    userPreferences = state.userPreferences,
                    analyticsHelper = analyticsHelper,
                ) {
                    PdfViewerScreen(
                        documentInfo = doc,
                        onBack = { finishAffinity() },
                        showBackButton = true,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveDocument(intent)?.let { document = it }
    }

    /** Extracts the incoming PDF URI (from VIEW or SEND) and adapts it into a [BasicDocument]. */
    private fun resolveDocument(intent: Intent?): BasicDocument? {
        val uri: Uri? =
            when (intent?.action) {
                Intent.ACTION_SEND ->
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                else -> intent?.data
            }
        if (uri == null) return null

        // Persist read access when the provider allows it; harmless otherwise.
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "PDF"
        return BasicDocument(
            uuid = UUID.randomUUID().toString(),
            filename = displayName,
            uri = uri.toString(),
            title = displayName,
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme != "content") return null
        return runCatching {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                        cursor.getString(index)
                    } else null
                }
            }
            .getOrNull()
    }
}
