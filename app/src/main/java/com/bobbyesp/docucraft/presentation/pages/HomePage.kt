package com.bobbyesp.docucraft.presentation.pages

import android.app.Activity
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.bobbyesp.docucraft.R
import com.bobbyesp.docucraft.presentation.common.LocalSnackbarHostState
import com.bobbyesp.docucraft.presentation.theme.DocucraftTheme
import com.bobbyesp.utilities.Toast
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch

@Composable
fun HomePage(viewModel: HomePageViewModel) {
    val activity = LocalContext.current as Activity
    val snackbarHost = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    val scannerOptions = GmsDocumentScannerOptions.Builder()
        .setScannerMode(SCANNER_MODE_FULL)
        .setGalleryImportAllowed(true)
        .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
        .build()
    val scanner = GmsDocumentScanning.getClient(scannerOptions)

    val scannerLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if(result.resultCode == Activity.RESULT_OK) {
            val scannerResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pdf = scannerResult?.pdf ?: throw Exception("The PDF is null!")
            val savedPdf = viewModel.savePdf(activity, pdf)

            if(savedPdf.path != null) {
                scope.launch {
                    val snackbarResult = snackbarHost.showSnackbar(message = activity.getString(R.string.pdf_saved_correctly), actionLabel = activity.getString(R.string.open_pdf))

                    when(snackbarResult) {
                        SnackbarResult.Dismissed -> {}
                        SnackbarResult.ActionPerformed -> viewModel.openPdfInViewer(activity, savedPdf.path)
                    }
                }
            } else {
                scope.launch {
                    snackbarHost.showSnackbar(activity.getString(R.string.pdf_saving_error))
                }
            }
        }
    }

    HomePageImpl(
        onScanNewDocument = {
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener {
                    scannerLauncher.launch(
                        IntentSenderRequest.Builder(it).build()
                    )
                }
                .addOnFailureListener {
                    Toast.makeToast(activity, activity.getString(R.string.scanner_open_failed))
                }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomePageImpl(
    onScanNewDocument: () -> Unit = {},
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(title = {
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onScanNewDocument) {
                Icon(
                    imageVector = Icons.Rounded.DocumentScanner,
                    contentDescription = stringResource(
                        id = R.string.scan_new_document
                    )
                )
            }
        }
    ) {

    }
}

@Preview
@Preview(uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun HomePagePreview() {
    DocucraftTheme {
        HomePageImpl()
    }
}