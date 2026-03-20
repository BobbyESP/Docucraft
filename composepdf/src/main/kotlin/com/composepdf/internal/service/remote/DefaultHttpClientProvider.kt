package com.composepdf.internal.service.remote

import com.composepdf.DownloadError
import com.composepdf.DownloadResult
import com.composepdf.ErrorType
import com.composepdf.HttpClientProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection

/**
 * Default HTTP client implementation using [HttpURLConnection].
 *
 * This implementation:
 * - Uses only standard Android APIs (no external dependencies)
 * - Streams directly to disk without memory buffering
 * - Supports cancellation via coroutine cancellation
 * - Enforces HTTPS-only connections
 *
 */
class DefaultHttpClientProvider : HttpClientProvider {

    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        outputFile: File,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {

        // Validate URL is HTTPS
        if (!url.startsWith("https://", ignoreCase = true)) {
            return@withContext DownloadResult.Failure(
                DownloadError(
                    type = ErrorType.INVALID_URL,
                    message = "Only HTTPS URLs are supported for security"
                )
            )
        }

        var connection: HttpURLConnection? = null

        try {
            val urlObj = URL(url)
            connection = urlObj.openConnection() as HttpsURLConnection

            // Configure connection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doInput = true

                // Add headers (without logging sensitive values)
                headers.forEach { (key, value) ->
                    setRequestProperty(key, value)
                }
            }

            // Check cancellation before connecting
            coroutineContext.ensureActive()

            connection.connect()

            val responseCode = connection.responseCode

            // Handle HTTP errors
            when (responseCode) {
                HttpURLConnection.HTTP_OK -> { /* Success, continue */
                }

                HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    return@withContext DownloadResult.Failure(
                        DownloadError(
                            type = ErrorType.AUTH_401,
                            message = "Authentication required",
                            httpCode = 401
                        )
                    )
                }

                HttpURLConnection.HTTP_FORBIDDEN -> {
                    return@withContext DownloadResult.Failure(
                        DownloadError(
                            type = ErrorType.AUTH_403,
                            message = "Access forbidden",
                            httpCode = 403
                        )
                    )
                }

                HttpURLConnection.HTTP_NOT_FOUND -> {
                    return@withContext DownloadResult.Failure(
                        DownloadError(
                            type = ErrorType.NOT_FOUND,
                            message = "Resource not found",
                            httpCode = 404
                        )
                    )
                }

                else -> {
                    if (responseCode >= 400) {
                        return@withContext DownloadResult.Failure(
                            DownloadError(
                                type = ErrorType.HTTP_ERROR,
                                message = "HTTP error: $responseCode",
                                httpCode = responseCode
                            )
                        )
                    }
                }
            }

            val contentLength = connection.contentLengthLong.takeIf { it > 0 }

            // Stream to disk
            val tempFile = File(outputFile.parent, "${outputFile.name}.tmp")

            try {
                BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Long = 0
                        var count: Int

                        while (input.read(buffer).also { count = it } != -1) {
                            // Check cancellation during download
                            coroutineContext.ensureActive()

                            output.write(buffer, 0, count)
                            bytesRead += count

                            onProgress(bytesRead, contentLength)
                        }

                        output.flush()
                    }
                }

                // Verify file was written
                if (tempFile.length() == 0L) {
                    tempFile.delete()
                    return@withContext DownloadResult.Failure(
                        DownloadError(
                            type = ErrorType.IO,
                            message = "Downloaded file is empty"
                        )
                    )
                }

                // Move temp file to final location
                tempFile.renameTo(outputFile)

                DownloadResult.Success(
                    file = outputFile,
                    contentLength = outputFile.length()
                )

            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }

        } catch (e: CancellationException) {
            DownloadResult.Failure(
                DownloadError(
                    type = ErrorType.CANCELLED,
                    message = "Download cancelled",
                    cause = e
                )
            )
        } catch (e: UnknownHostException) {
            DownloadResult.Failure(
                DownloadError(
                    type = ErrorType.NETWORK,
                    message = "Unable to resolve host",
                    cause = e
                )
            )
        } catch (e: SocketTimeoutException) {
            DownloadResult.Failure(
                DownloadError(
                    type = ErrorType.NETWORK,
                    message = "Connection timed out",
                    cause = e
                )
            )
        } catch (e: IOException) {
            DownloadResult.Failure(
                DownloadError(
                    type = ErrorType.IO,
                    message = e.message ?: "I/O error during download",
                    cause = e
                )
            )
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val BUFFER_SIZE = 8 * 1024 // 8KB
    }
}
