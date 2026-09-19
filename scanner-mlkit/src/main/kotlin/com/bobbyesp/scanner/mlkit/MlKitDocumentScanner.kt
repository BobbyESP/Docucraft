/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 */
package com.bobbyesp.scanner.mlkit

import android.app.Activity
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import com.bobbyesp.scanner.DocumentScanner
import com.bobbyesp.scanner.ScanError
import com.bobbyesp.scanner.ScanOutcome
import com.bobbyesp.scanner.ScanOutputFormat
import com.bobbyesp.scanner.ScanRequest
import com.bobbyesp.scanner.ScannerCapabilities
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.ApiException
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * [DocumentScanner] backed by the ML Kit document scanner that ships with Google Play Services.
 *
 * The whole point of this class is that it is the only one that knows any of that. Capture runs in
 * the Play Services process and answers through an activity result, so the IntentSender round trip
 * is folded back into a single suspending call here rather than being spread across the Activity, a
 * bus and a ViewModel.
 */
class MlKitDocumentScanner(
    private val host: ActivityResultHost,
    private val clock: () -> Long = System::currentTimeMillis,
) : DocumentScanner {

    override val capabilities =
        ScannerCapabilities(
            supportedOutputs = setOf(ScanOutputFormat.PDF, ScanOutputFormat.JPEG),
            supportsGalleryImport = true,
            maxPages = null,
            // Capture happens in the Play Services process, holding its own permission, which is
            // why this app declares none.
            requiresCameraPermission = false,
        )

    override suspend fun scan(request: ScanRequest): ScanOutcome {
        val activityResult =
            try {
                val client = GmsDocumentScanning.getClient(request.toOptions())
                val intentSender = host.withActivity { client.getStartScanIntent(it).await() }
                host.launchIntentSender(IntentSenderRequest.Builder(intentSender).build())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ScanOutcome.Failed(e.toScanError())
            }

        return activityResult.toOutcome()
    }

    /**
     * The Play Services scanner keeps running after this process is killed for memory, and the
     * activity result registry re-delivers once the app is rebuilt. This claims that result so the
     * scan the user actually finished is not thrown away.
     */
    override suspend fun resumePendingScan(): ScanOutcome? = host.awaitPendingResult()?.toOutcome()

    private fun ActivityResult.toOutcome(): ScanOutcome {
        val scan =
            try {
                GmsDocumentScanningResult.fromActivityResultIntent(data)
            } catch (e: Exception) {
                return ScanOutcome.Failed(ScanError.Engine(e))
            }

        // No payload at all means the scanner came back without a scan, which is what backing out
        // of it looks like.
        return ScanResultMapper.toOutcome(
            completed = resultCode == Activity.RESULT_OK && scan != null,
            pdfUri = scan?.pdf?.uri?.toString(),
            pageCount = scan?.pdf?.pageCount,
            pageUris = scan?.pages?.map { it.imageUri.toString() },
            capturedAtEpochMillis = clock(),
        )
    }
}

private fun ScanRequest.toOptions(): GmsDocumentScannerOptions {
    val formats =
        outputs.ifEmpty { setOf(ScanOutputFormat.PDF) }.map { it.toResultFormat() }.toIntArray()

    return GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(allowGalleryImport)
        .setPageLimit(maxPages ?: Int.MAX_VALUE)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .setResultFormats(formats.first(), *formats.drop(1).toIntArray())
        .build()
}

private fun ScanOutputFormat.toResultFormat(): Int =
    when (this) {
        ScanOutputFormat.PDF -> GmsDocumentScannerOptions.RESULT_FORMAT_PDF
        ScanOutputFormat.JPEG -> GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
    }

/**
 * Play Services reports a missing or unusable module as an [ApiException] carrying a
 * [ConnectionResult] code. That case is worth naming, because no retry will fix it; everything else
 * is opaque engine trouble.
 */
private fun Throwable.toScanError(): ScanError =
    if (this is ApiException && statusCode in ENGINE_UNAVAILABLE_CODES) {
        ScanError.EngineUnavailable
    } else {
        ScanError.Engine(this)
    }

private val ENGINE_UNAVAILABLE_CODES =
    setOf(
        ConnectionResult.SERVICE_MISSING,
        ConnectionResult.SERVICE_DISABLED,
        ConnectionResult.SERVICE_INVALID,
        ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
    )
