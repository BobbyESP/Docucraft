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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.contract.ViewerDocumentState
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens.PdfViewerScreen
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent
import org.koin.core.parameter.parametersOf

/**
 * Standalone activity that lets Docucraft act as a system PDF viewer for documents outside the app.
 *
 * Registered with `ACTION_VIEW` / `ACTION_SEND` intent-filters for `application/pdf`, it wraps the
 * incoming URI into a [ViewerDocumentRef.External] and reuses [PdfViewerScreen]. It is
 * intentionally separate from [com.bobbyesp.docucraft.MainActivity] so the main app's single back
 * stack stays untouched — back here simply finishes and returns to the calling app.
 */
class PdfViewerActivity : ComponentActivity(), KoinComponent {

    private val settingsRepository: SettingsRepository by inject()
    private val inAppNotificationsService: InAppNotificationsService by inject()
    private val analyticsHelper: AnalyticsHelper by inject()
    private val mainViewModel: MainViewModel by viewModel()

    private var document by mutableStateOf<ViewerDocumentRef.External?>(null)

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
            val state = uiState
            val ref = document
            if (state is MainActivityUiState.Success && ref != null) {
                AppLocalSettingsProvider(
                    inAppNotificationsService = inAppNotificationsService,
                    imageLoader = imageLoader,
                    settingsRepository = settingsRepository,
                    userPreferences = state.userPreferences,
                    analyticsHelper = analyticsHelper,
                ) {
                    // Keyed by the document, so a new one arriving through onNewIntent gets its
                    // own.
                    val viewModel: PdfViewerViewModel =
                        koinViewModel(key = ref.uri) { parametersOf(ref) }
                    val viewerState by viewModel.state.collectAsStateWithLifecycle()

                    (viewerState.document as? ViewerDocumentState.Open)?.let { open ->
                        PdfViewerScreen(
                            viewModel = viewModel,
                            documentInfo = open.document,
                            onBack = { finishAffinity() },
                            showBackButton = true,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveDocument(intent)?.let { document = it }
    }

    /** Extracts the incoming PDF URI (from VIEW or SEND) and names it for the viewer. */
    private fun resolveDocument(intent: Intent?): ViewerDocumentRef.External? {
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
        return ViewerDocumentRef.External(uri = uri.toString(), displayName = displayName)
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
