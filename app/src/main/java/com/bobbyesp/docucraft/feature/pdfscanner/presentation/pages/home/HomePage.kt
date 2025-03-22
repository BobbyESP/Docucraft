package com.bobbyesp.docucraft.feature.pdfscanner.presentation.pages.home

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.core.util.state.ResourceState
import com.bobbyesp.docucraft.feature.pdfscanner.domain.model.ScannedPdf
import com.bobbyesp.docucraft.feature.pdfscanner.presentation.components.card.ScannedPdfCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    scannedPdfsState: State<ResourceState<List<ScannedPdf>>>,
    onEvent: (HomeViewModel.Event) -> Unit,
) {
    val activity = LocalActivity.current

    val scannedPdfs = scannedPdfsState.value

    val scannerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            onEvent(HomeViewModel.Event.HandlePdfScanningResult(result))
        }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier,
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(id = R.string.app_name).uppercase(),
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleLarge,
                            letterSpacing = 4.sp,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(text = stringResource(id = R.string.scan)) },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.DocumentScanner,
                        contentDescription = stringResource(id = R.string.scan_new_document),
                    )
                },
                onClick = {
                    onEvent(
                        HomeViewModel.Event.ScanPdf(activity = activity, listener = scannerLauncher)
                    )
                },
            )
        },
    ) { padding ->
        Crossfade(targetState = scannedPdfs) { state ->
            when (state) {
                is ResourceState.Loading<*> -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is ResourceState.Error<*> -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.message ?: stringResource(id = R.string.error_loading_pdfs)
                        )
                    }
                }

                is ResourceState.Success<*> -> {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
                        items(state.data ?: emptyList()) { scannedPdf ->
                            ScannedPdfCard(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .animateItem(fadeInSpec = null, fadeOutSpec = null),
                                pdf = scannedPdf,
                                onOpenPdf = { uri ->
                                    onEvent(HomeViewModel.Event.PdfAction.Open(uri))
                                },
                                onSharePdf = { uri ->
                                    onEvent(HomeViewModel.Event.PdfAction.Share(uri))
                                },
                                onDeletePdf = { uri ->
                                    onEvent(HomeViewModel.Event.WarnAboutDeletion(uri))
                                },
                                onSavePdf = {
                                    onEvent(HomeViewModel.Event.PdfAction.Save(scannedPdf))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
