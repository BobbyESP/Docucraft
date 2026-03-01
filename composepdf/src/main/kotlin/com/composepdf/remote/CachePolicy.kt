package com.composepdf.remote

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Configuration for PDF disk cache behavior.
 * 
 * Controls how downloaded PDFs are cached, when they expire, and how much
 * disk space can be used.
 * 
 * Example usage:
 * ```kotlin
 * // Cache for 1 day, max 50MB
 * val policy = CachePolicy(
 *     maxAge = 1.days,
 *     maxSizeBytes = 50 * 1024 * 1024
 * )
 * 
 * // No caching - always download fresh
 * val noCache = CachePolicy.NoCache
 * ```
 */
data class CachePolicy(
    /**
     * Maximum age of cached files before they expire.
     * Files older than this will be re-downloaded.
     */
    val maxAge: Duration = 7.days,
    
    /**
     * Maximum total size of the cache directory in bytes.
     * When exceeded, oldest files are evicted using LRU strategy.
     */
    val maxSizeBytes: Long = 100 * 1024 * 1024, // 100MB
    
    /**
     * Whether to check file validity on each access.
     * If true, expired files trigger re-download even if they exist.
     */
    val validateOnAccess: Boolean = true,
    
    /**
     * Whether to keep files in cache after they expire.
     * If true, expired files can be used while fresh copy downloads.
     */
    val staleWhileRevalidate: Boolean = false
) {
    companion object {
        /**
         * Default cache policy: 7 days, 100MB max.
         */
        val Default = CachePolicy()
        
        /**
         * No caching - always download fresh copy.
         */
        val NoCache = CachePolicy(maxAge = Duration.ZERO)
        
        /**
         * Aggressive caching - 30 days, 500MB max.
         */
        val Aggressive = CachePolicy(
            maxAge = 30.days,
            maxSizeBytes = 500 * 1024 * 1024
        )
    }
}
