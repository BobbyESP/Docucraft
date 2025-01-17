package com.bobbyesp.docucraft.presentation.pages

import android.app.Activity
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPasteOff
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.domain.model.SavedPdf
import com.bobbyesp.docucraft.presentation.common.LocalSnackbarHostState
import com.bobbyesp.docucraft.presentation.components.card.SavedPdfCardTransitionsWrapper
import com.bobbyesp.docucraft.presentation.components.card.SavedPdfListItemTransitionsWrapper
import com.bobbyesp.docucraft.presentation.theme.DocucraftTheme
import com.bobbyesp.utilities.Toast
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionActionable

@Composable
fun HomePage(
    viewModel: HomePageViewModel
) {
    val activity = LocalContext.current as Activity
    val snackbarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    val scannerOptions = GmsDocumentScannerOptions.Builder().setScannerMode(SCANNER_MODE_FULL)
        .setGalleryImportAllowed(true).setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
        .build()
    val scanner = GmsDocumentScanning.getClient(scannerOptions)

    val scannerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scannerResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                val pdf = scannerResult?.pdf ?: throw Exception("The PDF is null!")
                val savedPdf = viewModel.savePdf(activity, pdf)

                scope.launch {
                    viewModel.savePdfToDatabase(savedPdf)
                }

                if (savedPdf.path != null) {
                    scope.launch {
                        val snackbarResult = snackbarHost.showSnackbar(
                            message = activity.getString(R.string.pdf_saved_correctly),
                            actionLabel = activity.getString(R.string.open_pdf),
                            duration = SnackbarDuration.Short
                        )

                        when (snackbarResult) {
                            SnackbarResult.Dismissed -> {}
                            SnackbarResult.ActionPerformed -> viewModel.openPdfInViewer(
                                activity, savedPdf.path
                            )
                        }
                    }
                } else {
                    scope.launch {
                        snackbarHost.showSnackbar(activity.getString(R.string.pdf_saving_error))
                    }
                }
            }
        }

    HomePageImpl(onScanNewDocument = {
        scanner.getStartScanIntent(activity).addOnSuccessListener {
            scannerLauncher.launch(
                IntentSenderRequest.Builder(it).build()
            )
        }.addOnFailureListener {
            Toast.makeToast(activity, activity.getString(R.string.scanner_open_failed))
        }
    }, savedPdfs = viewModel.savedPdfs, onOpenPdf = {
        viewModel.openPdfInViewer(activity, it.path!!)
    }, onSharePdf = { pdf ->
        pdf.path?.let {
            viewModel.sharePdf(activity, it)
        }

        if (pdf.path == null) scope.launch {
            snackbarHost.showSnackbar(activity.getString(R.string.error_sharing_pdf))
        }
    }, onDeletePdf = {
        viewModel.viewModelScope.launch {
            val successfullyDeleted = viewModel.deletePdf(activity, it)

            snackbarHost.showSnackbar(
                if (successfullyDeleted) activity.getString(R.string.file_deleted_successfully) else activity.getString(
                    R.string.error_deleting_file
                )
            )
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun HomePageImpl(
    savedPdfs: StateFlow<List<SavedPdf>>? = null,
    onScanNewDocument: () -> Unit = {},
    onOpenPdf: (SavedPdf) -> Unit = {},
    onSharePdf: (SavedPdf) -> Unit = {},
    onDeletePdf: (SavedPdf) -> Unit = {}
) {
    val lazyListState = rememberLazyListState()
    val pdfs = savedPdfs?.collectAsStateWithLifecycle()?.value

    var showDeleteDialog by remember {
        mutableStateOf(false)
    }

    var selectedPdf by remember {
        mutableStateOf<SavedPdf?>(null)
    }

    Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
        CenterAlignedTopAppBar(modifier = Modifier, title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(id = R.string.app_name).uppercase(),
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleLarge.copy(
                        letterSpacing = 4.sp,
                    ),
                )
            }
        })
    }, floatingActionButton = {
        ExtendedFloatingActionButton(text = {
            Text(text = stringResource(id = R.string.scan))
        }, icon = {
            Icon(
                imageVector = Icons.Rounded.DocumentScanner,
                contentDescription = stringResource(
                    id = R.string.scan_new_document
                )
            )
        }, onClick = onScanNewDocument
        )
    }) { paddingValues ->
        if (pdfs.isNullOrEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NoPDFsCard(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    onScanNewDocument = onScanNewDocument
                )
            }
        } else {
            SharedTransitionLayout {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    LazyColumnScrollbar(
                        listState = lazyListState,
                        thumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        thumbSelectedColor = MaterialTheme.colorScheme.primary,
                        selectionActionable = ScrollbarSelectionActionable.WhenVisible,
                    ) {
                        var cardPdf by remember {
                            mutableStateOf<SavedPdf?>(null)
                        }
                        AnimatedContent(
                            modifier = Modifier,
                            targetState = cardPdf,
                            label = "Shared transition holder parent layout - Home Page"
                        ) { newPdf ->
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                                state = lazyListState,
                            ) {
                                items(count = pdfs.size,
                                    key = { index -> pdfs[index].savedTimestamp },
                                    contentType = { index -> pdfs[index].savedTimestamp.toString() }) { index ->
                                    val pdf = pdfs[index]

                                    SavedPdfListItemTransitionsWrapper(pdf = pdf,
                                        onClick = { onOpenPdf(pdf) },
                                        onLongPressed = {
                                            cardPdf = pdf
                                        },
                                        visible = newPdf != pdf
                                    )
                                }
                            }
                            newPdf?.let {
                                SavedPdfCardTransitionsWrapper(modifier = Modifier,
                                    pdf = it,
                                    onShareRequest = onSharePdf,
                                    onOpenPdf = { cardPdf?.let { pdf -> onOpenPdf(pdf) } },
                                    onDeleteRequest = { pdfToRemove ->
                                        selectedPdf = pdfToRemove
                                        showDeleteDialog = !showDeleteDialog
                                    }) {
                                    cardPdf = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog && selectedPdf != null) {
        DeleteFileDialog(pdf = selectedPdf!!, onDeletePdf = {
            onDeletePdf(selectedPdf!!)
            selectedPdf = null
        }, onReturnToPage = {
            showDeleteDialog = false
            selectedPdf = null
        })
    }
}


@Composable
fun NoPDFsCard(modifier: Modifier = Modifier, onScanNewDocument: () -> Unit) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(), onClick = onScanNewDocument
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                modifier = Modifier
                    .size(64.dp)
                    .clip(
                        CircleShape
                    )
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(12.dp),
                imageVector = Icons.Rounded.ContentPasteOff,
                contentDescription = stringResource(id = R.string.no_pdfs),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.no_pdfs_scanned),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(id = R.string.no_pdfs_scanned_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DeleteFileDialog(
    pdf: SavedPdf, onDeletePdf: (SavedPdf) -> Unit, onReturnToPage: () -> Unit = {}
) {
    AlertDialog(onDismissRequest = onReturnToPage, icon = {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = stringResource(id = R.string.warning)
        )
    }, title = {
        Text(text = stringResource(id = R.string.delete_file_title))
    }, text = {
        Text(
            text = stringResource(id = R.string.delete_file_desc),
            style = MaterialTheme.typography.bodyMedium
        )
    }, dismissButton = {
        TextButton(
            onClick = onReturnToPage,
        ) {
            Text(text = stringResource(id = R.string.return_str))
        }
    }, confirmButton = {
        Button(
            onClick = {
                onDeletePdf(pdf)
                onReturnToPage()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text(text = stringResource(id = R.string.delete))
        }
    })
}

@Preview
@Composable
private fun DeleteFileDialogPrev() {
    DeleteFileDialog(pdf = SavedPdf.emptyPdf(), onDeletePdf = { SavedPdf.emptyPdf() }) {}
}

@Preview
@Composable
private fun EmptyPDFsCard() {
    NoPDFsCard() {}
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun HomePagePreview() {
    DocucraftTheme {
        HomePageImpl()
    }
}