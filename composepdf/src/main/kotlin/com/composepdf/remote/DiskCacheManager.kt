package com.composepdf.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Manages disk caching of downloaded PDF files.
 * 
 * Features:
 * - LRU eviction when cache size exceeded
 * - Automatic cleanup of expired files
 * - Thread-safe cache operations
 * - SHA-256 hashing for cache keys
 * 
 * @property context Android context for cache directory access
 * @property cacheDir Custom cache directory, or null to use default
 */
class DiskCacheManager(
    private val context: Context,
    private val cacheDir: File? = null
) {
    
    private val mutex = Mutex()
    
    private val actualCacheDir: File by lazy {
        cacheDir ?: File(context.cacheDir, CACHE_SUBDIRECTORY).also {
            it.mkdirs()
        }
    }
    
    /**
     * Gets a cached file for the given key, or null if not cached or expired.
     * 
     * @param cacheKey The cache key (will be hashed)
     * @param policy The cache policy to apply
     * @return The cached file if valid, or null
     */
    suspend fun get(cacheKey: String, policy: CachePolicy): File? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val hashedKey = hashKey(cacheKey)
            val cacheFile = File(actualCacheDir, "$hashedKey.pdf")
            
            if (!cacheFile.exists()) {
                return@withContext null
            }
            
            // Check expiration
            if (policy.validateOnAccess) {
                val age = System.currentTimeMillis() - cacheFile.lastModified()
                if (age > policy.maxAge.inWholeMilliseconds) {
                    // File expired
                    if (!policy.staleWhileRevalidate) {
                        cacheFile.delete()
                        return@withContext null
                    }
                }
            }
            
            // Update access time for LRU
            cacheFile.setLastModified(System.currentTimeMillis())
            
            cacheFile
        }
    }
    
    /**
     * Gets the file path for a cache entry (for writing).
     * 
     * @param cacheKey The cache key (will be hashed)
     * @return The file path where the cached file should be written
     */
    suspend fun getCacheFile(cacheKey: String): File = mutex.withLock {
        withContext(Dispatchers.IO) {
            val hashedKey = hashKey(cacheKey)
            File(actualCacheDir, "$hashedKey.pdf").also {
                // Ensure parent directory exists
                it.parentFile?.mkdirs()
            }
        }
    }
    
    /**
     * Marks a cache entry as complete (after successful download).
     * 
     * @param cacheKey The cache key
     * @param file The downloaded file
     * @param policy The cache policy for cleanup
     */
    suspend fun put(cacheKey: String, file: File, policy: CachePolicy) = mutex.withLock {
        withContext(Dispatchers.IO) {
            // Update modification time
            file.setLastModified(System.currentTimeMillis())
            
            // Enforce cache size limit
            enforceSizeLimit(policy.maxSizeBytes)
        }
    }
    
    /**
     * Removes a specific cache entry.
     * 
     * @param cacheKey The cache key to remove
     */
    suspend fun remove(cacheKey: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val hashedKey = hashKey(cacheKey)
            File(actualCacheDir, "$hashedKey.pdf").delete()
        }
    }
    
    /**
     * Clears all cached files.
     */
    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) {
            actualCacheDir.listFiles()?.forEach { it.delete() }
        }
    }
    
    /**
     * Gets the current cache size in bytes.
     */
    suspend fun size(): Long = mutex.withLock {
        withContext(Dispatchers.IO) {
            actualCacheDir.listFiles()?.sumOf { it.length() } ?: 0L
        }
    }
    
    /**
     * Cleans up expired cache entries.
     * 
     * @param maxAge Maximum age in milliseconds
     */
    suspend fun cleanExpired(maxAge: Long) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            actualCacheDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > maxAge) {
                    file.delete()
                }
            }
        }
    }
    
    private fun enforceSizeLimit(maxSizeBytes: Long) {
        val files = actualCacheDir.listFiles()?.toMutableList() ?: return
        
        var totalSize = files.sumOf { it.length() }
        
        if (totalSize <= maxSizeBytes) return
        
        // Sort by last modified (oldest first) for LRU eviction
        files.sortBy { it.lastModified() }
        
        for (file in files) {
            if (totalSize <= maxSizeBytes) break
            
            val fileSize = file.length()
            if (file.delete()) {
                totalSize -= fileSize
            }
        }
    }
    
    private fun hashKey(key: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }
    
    companion object {
        private const val CACHE_SUBDIRECTORY = "pdf_cache"
    }
}
