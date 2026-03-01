package com.composepdf.remote

import java.io.File

/**
 * Abstraction for HTTP clients to enable pluggable network implementations.
 * 
 * This interface allows the library to work with different HTTP clients
 * (OkHttp, Ktor, HttpURLConnection, etc.) without hard dependencies.
 * 
 * Security requirements:
 * - MUST stream response directly to disk (no memory buffering)
 * - MUST NOT log headers or tokens
 * - MUST support cancellation via coroutine cancellation
 * 
 * Example implementation:
 * ```kotlin
 * class MyHttpClient : HttpClientProvider {
 *     override suspend fun download(
 *         url: String,
 *         headers: Map<String, String>,
 *         outputFile: File,
 *         onProgress: (Long, Long?) -> Unit
 *     ): DownloadResult {
 *         // Implementation
 *     }
 * }
 * ```
 */
interface HttpClientProvider {
    
    /**
     * Downloads a file from the given URL to the specified output file.
     * 
     * @param url The URL to download from (must be HTTPS)
     * @param headers HTTP headers to include in the request
     * @param outputFile The file to write the downloaded content to
     * @param onProgress Callback for download progress updates
     *                   Parameters: (bytesRead, totalBytes or null if unknown)
     * @return [DownloadResult] indicating success or failure
     */
    suspend fun download(
        url: String,
        headers: Map<String, String>,
        outputFile: File,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit
    ): DownloadResult
}

/**
 * Result of a download operation.
 */
sealed class DownloadResult {
    
    /**
     * Download completed successfully.
     * 
     * @property file The downloaded file
     * @property contentLength The size of the downloaded file in bytes
     */
    data class Success(
        val file: File,
        val contentLength: Long
    ) : DownloadResult()
    
    /**
     * Download failed.
     * 
     * @property error The error details
     */
    data class Failure(
        val error: DownloadError
    ) : DownloadResult()
}

/**
 * Details about a download failure.
 */
data class DownloadError(
    val type: ErrorType,
    val message: String,
    val httpCode: Int? = null,
    val cause: Throwable? = null
)
