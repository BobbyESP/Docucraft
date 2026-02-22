package com.bobbyesp.docucraft

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.bhuvaneshw.pdf.compose.PdfSource
import com.bobbyesp.docucraft.core.domain.repository.InAppNotificationsService
import com.bobbyesp.docucraft.core.presentation.common.Route
import com.bobbyesp.docucraft.core.presentation.navigation.TopLevelBackStack
import com.bobbyesp.docucraft.feature.docscanner.presentation.screens.home.HomeScreen
import com.bobbyesp.docucraft.feature.pdfviewer.presentation.screens.PdfViewerScreen
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Navigator(
    rootBackStack: TopLevelBackStack<Route>,
    notificationService: InAppNotificationsService
) {
    val onBack: () -> Unit = { rootBackStack.pop() }

    NavDisplay(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        backStack = rootBackStack.backStack,
        entryDecorators = persistentListOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        onBack = onBack,
        entryProvider = entryProvider {
            entry<Route.Home> {
                HomeScreen(
                    onNavigate = { rootBackStack.push(it) },
                    notificationService = notificationService
                )
            }
            entry<Route.PdfViewer> { key ->
                PdfViewerScreen(
                    source = PdfSource.ContentUri(key.documentUri.toUri()),
                    onBack = onBack
                )
            }
        },
        transitionSpec = {
            // Slide in from right when navigating forward
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            // Slide in from left when navigating back
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            // Slide in from left when navigating back
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
        },
    )
}
