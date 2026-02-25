package com.bobbyesp.docucraft.feature.docscanner.domain

import com.bobbyesp.docucraft.feature.docscanner.domain.model.RawScanResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Manages the communication between ViewModels and the MainActivity regarding document scanning operations.
 *
 * This class acts as a singleton bridge (or event bus) to decouple the UI logic from the Android Activity lifecycle.
 * It allows ViewModels to request a scan without knowing about Intents, and allows the Activity to deliver
 * results back to the requesting ViewModel.
 */
class ScannerManager {

    // Channel to send scan requests from ViewModels to the Activity.
    private val _scanRequestChannel = Channel<Unit>(Channel.BUFFERED)

    /**
     * A flow emitting scan requests.
     * The MainActivity should observe this flow to trigger the GmsDocumentScanner intent.
     */
    val scanRequest: Flow<Unit> = _scanRequestChannel.receiveAsFlow()

    // Channel to send scan results from the Activity back to the ViewModels.
    private val _scanResultChannel = Channel<Result<RawScanResult>>(Channel.BUFFERED)

    /**
     * A flow emitting the results of the scanning process.
     * ViewModels should observe this flow to receive the processed document data (URI, page count, etc.)
     * or handle potential failures.
     */
    val scanResult: Flow<Result<RawScanResult>> = _scanResultChannel.receiveAsFlow()

    /**
     * Initiates a document scan request.
     *
     * This method should be called by a ViewModel. It emits a generic event to the [scanRequest] flow,
     * signaling the MainActivity to launch the scanner UI.
     */
    suspend fun requestScan() {
        _scanRequestChannel.send(Unit)
    }

    /**
     * Delivers the result of a document scan.
     *
     * This method should be called by the MainActivity after the scanner Activity result has been
     * processed and mapped to a [RawScanResult].
     *
     * @param result A [Result] containing either the successful [RawScanResult] or an exception (failure/cancellation).
     */
    suspend fun onScanResult(result: Result<RawScanResult>) {
        _scanResultChannel.send(result)
    }
}
