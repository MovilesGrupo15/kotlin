package edu.uniandes.ecosnap.data.cache

import android.graphics.Bitmap
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Memory-aware LRU cache with intelligent size management and metrics
 */
class InMemoryCache<K, V>(
    private val maxMemoryBytes: Long = 8 * 1024 * 1024, // 8MB default
    private val maxEntries: Int = 200,
    private val ttlMillis: Long = 5 * 60 * 1000 // 5 minutes TTL
) : Cache<K, V> {

    private data class CacheEntry<V>(
        val value: V,
        val timestamp: Long,
        val accessCount: Long,
        val sizeBytes: Long
    )

    private val cache = LinkedHashMap<K, CacheEntry<V>>(maxEntries, 0.75f, true)
    private val lock = ReentrantReadWriteLock()
    private var currentMemoryBytes = 0L
    private var accessCounter = 0L

    // Metrics for performance monitoring
    private var hitCount = 0L
    private var missCount = 0L
    private var evictionCount = 0L

    override fun get(key: K): V? = lock.read {
        val entry = cache[key]

        if (entry == null) {
            missCount++
            return null
        }

        // Check TTL
        val currentTime = System.currentTimeMillis()
        if (currentTime - entry.timestamp > ttlMillis) {
            // Entry expired, remove it
            lock.write {
                cache.remove(key)?.let { expired ->
                    currentMemoryBytes -= expired.sizeBytes
                }
            }
            missCount++
            return null
        }

        hitCount++
        return entry.value
    }

    override fun put(key: K, value: V) = lock.write {
        val sizeBytes = calculateSize(value)
        val currentTime = System.currentTimeMillis()

        // Remove existing entry if present
        cache.remove(key)?.let { oldEntry ->
            currentMemoryBytes -= oldEntry.sizeBytes
        }

        // Check if new entry would exceed memory limit
        if (sizeBytes > maxMemoryBytes) {
            Log.w("OptimizedCache", "Entry too large for cache: ${sizeBytes}B > ${maxMemoryBytes}B")
            return
        }

        // Evict entries to make space
        evictToMakeSpace(sizeBytes)

        // Add new entry
        val newEntry = CacheEntry(
            value = value,
            timestamp = currentTime,
            accessCount = ++accessCounter,
            sizeBytes = sizeBytes
        )

        cache[key] = newEntry
        currentMemoryBytes += sizeBytes

        // Final safety check for max entries
        while (cache.size > maxEntries) {
            evictLeastRecentlyUsed()
        }

        logCacheStats()
    }

    override fun remove(key: K): Unit = lock.write {
        cache.remove(key)?.let { entry ->
            currentMemoryBytes -= entry.sizeBytes
        }
    }

    override fun clear(): Unit = lock.write {
        cache.clear()
        currentMemoryBytes = 0L
        hitCount = 0L
        missCount = 0L
        evictionCount = 0L
        Log.d("OptimizedCache", "Cache cleared")
    }

    private fun evictToMakeSpace(requiredBytes: Long) {
        val targetMemory = maxMemoryBytes - requiredBytes

        while (currentMemoryBytes > targetMemory && cache.isNotEmpty()) {
            evictLeastRecentlyUsed()
        }
    }

    private fun evictLeastRecentlyUsed() {
        val eldestEntry = cache.entries.firstOrNull()
        if (eldestEntry != null) {
            cache.remove(eldestEntry.key)
            currentMemoryBytes -= eldestEntry.value.sizeBytes
            evictionCount++
        }
    }

    private fun calculateSize(value: V): Long {
        return when (value) {
            is String -> {
                // UTF-8 string size approximation
                value.length * 2L + 24L // 2 bytes per char + object overhead
            }
            is Bitmap -> {
                // Bitmap memory usage
                (value.width * value.height * 4L) + 32L // 4 bytes per pixel + overhead
            }
            is ByteArray -> {
                value.size.toLong() + 16L // Array size + overhead
            }
            is Collection<*> -> {
                // Estimate collection size
                val baseSize = value.size * 8L + 32L // Reference size + overhead
                baseSize + if (value.isNotEmpty()) {
                    // Sample first element to estimate element size
                    when (val first = value.first()) {
                        is String -> first.length * 2L * value.size
                        else -> 64L * value.size // Conservative estimate
                    }
                } else 0L
            }
            else -> {
                // Conservative estimate for unknown objects
                256L
            }
        }
    }

    private fun logCacheStats() {
        if ((hitCount + missCount) % 100 == 0L && (hitCount + missCount) > 0) {
            val hitRate = (hitCount.toDouble() / (hitCount + missCount) * 100).toInt()
            val memoryUsagePercent = (currentMemoryBytes.toDouble() / maxMemoryBytes * 100).toInt()

            Log.d("OptimizedCache", """
                Cache Stats: 
                - Hit Rate: $hitRate% ($hitCount/${ hitCount + missCount})
                - Memory: ${currentMemoryBytes / 1024}KB / ${maxMemoryBytes / 1024}KB ($memoryUsagePercent%)
                - Entries: ${cache.size}/$maxEntries
                - Evictions: $evictionCount
            """.trimIndent())
        }
    }

    // Method to cleanup expired entries proactively
    fun cleanupExpired() = lock.write {
        val currentTime = System.currentTimeMillis()
        val iterator = cache.entries.iterator()
        var cleanedCount = 0

        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTime - entry.value.timestamp > ttlMillis) {
                currentMemoryBytes -= entry.value.sizeBytes
                iterator.remove()
                cleanedCount++
            }
        }

        if (cleanedCount > 0) {
            Log.d("OptimizedCache", "Cleaned up $cleanedCount expired entries")
        }
    }

    // Method to force memory cleanup when app goes to background
    fun trimToSize(targetMemoryBytes: Long) = lock.write {
        while (currentMemoryBytes > targetMemoryBytes && cache.isNotEmpty()) {
            evictLeastRecentlyUsed()
        }
        Log.d("OptimizedCache", "Trimmed cache to ${currentMemoryBytes / 1024}KB")
    }

    // Diagnostic method
    fun getCacheMetrics(): Map<String, Any> = lock.read {
        mapOf(
            "hitCount" to hitCount,
            "missCount" to missCount,
            "hitRate" to if (hitCount + missCount > 0) (hitCount.toDouble() / (hitCount + missCount)) else 0.0,
            "memoryUsageBytes" to currentMemoryBytes,
            "memoryUsagePercent" to (currentMemoryBytes.toDouble() / maxMemoryBytes),
            "entryCount" to cache.size,
            "evictionCount" to evictionCount
        )
    }
}