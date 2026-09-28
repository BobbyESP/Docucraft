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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import coil.imageLoader
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.domain.preferences.SettingsRepository
import com.bobbyesp.docucraft.core.domain.repository.AnalyticsHelper
import com.bobbyesp.docucraft.core.domain.repository.InAppNotificationsService
import com.bobbyesp.docucraft.core.presentation.MainActivityUiState
import com.bobbyesp.docucraft.core.presentation.MainViewModel
import com.bobbyesp.docucraft.core.presentation.common.AppLocalSettingsProvider
import com.bobbyesp.docucraft.core.presentation.common.LocalDarkTheme
import com.bobbyesp.docucraft.core.presentation.navigation.DocucraftNavDisplay
import com.bobbyesp.docucraft.core.presentation.navigation.overlay.rememberOverlaySceneStrategy
import com.bobbyesp.docucraft.core.presentation.navigation.rememberNavigator
import com.bobbyesp.docucraft.core.presentation.notifications.SonnerNotificationServiceImpl
import com.bobbyesp.docucraft.feature.pdfviewer.domain.model.ViewerDocumentRef
import com.bobbyesp.docucraft.feature.pdfviewer.navigation.ExternalPdfViewer
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens.PdfViewerScreen
import com.dokar.sonner.Toaster
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.component.KoinComponent

/**
 * Standalone activity that lets Docucraft act as a system PDF viewer for documents outside the app.
 *
 * Registered with `ACTION_VIEW` / `ACTION_SEND` intent-filters for `application/pdf`, it wraps the
 * incoming URI into a [ViewerDocumentRef.External] and reuses [PdfViewerScreen]. It is
 * intentionally separate from [com.bobbyesp.docucraft.MainActivity] so the main app's single back
 * stack stays untouched — back here simply finishes and returns to the calling app.
 *
 * It runs in a task of its own (`taskAffinity=""` in the manifest), so a document opened from
 * another app never lands on top of the library, and leaving it never touches the library either.
 * Its card leaves Recents when it finishes. It used to share the app's task and leave through
 * `finishAffinity()`, which closed the library underneath as well (decision D5,
 * `docs/pdf-viewer.md`).
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

        val sonnerManager = inAppNotificationsService as SonnerNotificationServiceImpl

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
                    ExternalViewerHost(document = ref, onClose = ::finish)

                    // MainActivity has its own; without this one, messages raised in the external
                    // viewer were emitted and never shown.
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

/**
 * The external viewer's own back stack (decision D5): the document at the root, and whatever it
 * opens on top, such as its details, as ordinary destinations with the same state handling and
 * motion as the app's shell. Only overlays are laid out here; there is no list to sit beside.
 */
@Composable
private fun ExternalViewerHost(document: ViewerDocumentRef.External, onClose: () -> Unit) {
    val root = ExternalPdfViewer(uri = document.uri, displayName = document.displayName)
    val backStack = rememberNavBackStack(root)
    val navigator = rememberNavigator(backStack)
    val overlayStrategy = rememberOverlaySceneStrategy<NavKey>()
    val sceneStrategies = remember(overlayStrategy) { listOf(overlayStrategy) }

    // A different document arriving through onNewIntent replaces the stack, rather than changing
    // the document underneath a sheet that is still about the previous one. Added before the rest
    // is removed, because a NavDisplay must never see an empty stack.
    LaunchedEffect(root) {
        if (backStack.firstOrNull() != root) {
            backStack.add(root)
            backStack.removeAll { it != root }
        }
    }

    DocucraftNavDisplay(
        backStack = backStack,
        navigator = navigator,
        sceneStrategies = sceneStrategies,
        entryProvider = entryProvider { externalPdfViewerSection(navigator, onClose) },
    )
}
