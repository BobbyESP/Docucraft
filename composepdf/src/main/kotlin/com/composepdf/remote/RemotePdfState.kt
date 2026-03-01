package com.composepdf.remote

import java.io.File

/**
 * Represents the current state of a remote PDF download operation.
 * 
 * This sealed class provides type-safe state representation for tracking
 * the lifecycle of a remote PDF from request to ready-for-rendering.
 * 
 * Example usage:
 * ```kotlin
 * when (val state = remoteState) {
 *     is RemotePdfState.Idle -> { /* Initial state */ }
 *     is RemotePdfState.Downloading -> {
 *         state.progress?.let { progress ->
 *             LinearProgressIndicator(progress = progress)
 *         }
 *     }
 *     is RemotePdfState.Cached -> {
 *         // File is ready, pass to PdfRenderer
 *         renderPdf(state.file)
 *     }
 *     is RemotePdfState.Error -> {
 *         Text("Error: ${state.message}")
 *     }
 * }
 * ```
 */
sealed class RemotePdfState {
    
    /**
     * Initial state before any download has started.
     */
    data object Idle : RemotePdfState()
    
    /**
     * Download is in progress.
     * 
     * @property progress Download progress as a fraction (0.0 to 1.0), 
     *                    or null if the total size is unknown
     * @property bytesDownloaded Number of bytes downloaded so far
     * @property totalBytes Total file size in bytes, or null if unknown
     */
    data class Downloading(
        val progress: Float? = null,
        val bytesDownloaded: Long = 0,
        val totalBytes: Long? = null
    ) : RemotePdfState()
    
    /**
     * PDF has been successfully downloaded and cached.
     * 
     * @property file The local file containing the PDF, ready for rendering
     */
    data class Cached(val file: File) : RemotePdfState()
    
    /**
     * An error occurred during download or caching.
     * 
     * @property type The category of error
     * @property message Human-readable error description
     * @property cause The underlying exception, if any
     */
    data class Error(
        val type: ErrorType,
        val message: String,
        val cause: Throwable? = null
    ) : RemotePdfState()
}

/**
 * Categories of errors that can occur during remote PDF loading.
 */
enum class ErrorType {
    /** Network connectivity issues (no connection, timeout, etc.) */
    NETWORK,
    
    /** HTTP 401 Unauthorized response */
    AUTH_401,
    
    /** HTTP 403 Forbidden response */
    AUTH_403,
    
    /** HTTP 404 Not Found response */
    NOT_FOUND,
    
    /** Other HTTP error responses (4xx, 5xx) */
    HTTP_ERROR,
    
    /** Disk I/O failure (write, read, space) */
    IO,
    
    /** Downloaded file is not a valid PDF */
    CORRUPTED,
    
    /** Download was cancelled by the user or lifecycle */
    CANCELLED,
    
    /** URL is invalid or uses insecure HTTP */
    INVALID_URL
}
