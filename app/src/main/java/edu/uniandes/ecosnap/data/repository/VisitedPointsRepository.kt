package edu.uniandes.ecosnap.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import edu.uniandes.ecosnap.domain.model.VisitedPointItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.concurrent.ConcurrentHashMap

object VisitedPointsRepository {
    private const val PREFS_NAME = "ecosnap_visited_points"
    private const val VISITED_POINTS_KEY = "visited_points_data"
    private const val MAX_STORED_POINTS = 200

    private var sharedPrefs: SharedPreferences? = null
    private val json = Json { ignoreUnknownKeys = true }

    // ============= CACHING STRATEGY =============
    private val memoryCache = ConcurrentHashMap<String, VisitedPointItem>()
    private val cacheAccessOrder = mutableListOf<String>() // Para LRU
    private var cacheHitCount = 0
    private var cacheMissCount = 0
    private val maxCacheSize = 100 // Máximo elementos en memoria

    // Flags para evitar múltiples cargas
    private var isInitialized = false
    private var isLoading = false

    fun initialize(context: Context) {
        if (isInitialized) return

        sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadCacheFromStorage()
        isInitialized = true
        Log.d("VisitedPointsRepo", "Initialized with caching strategy")
    }

    fun markPointAsVisited(visitedPoint: VisitedPointItem) {
        try {
            // Añadir al cache inmediatamente
            addToMemoryCache(visitedPoint)

            // Guardar en storage
            saveToStorage(visitedPoint)

            Log.d("VisitedPointsRepo", "Point marked as visited: ${visitedPoint.pointName}")

        } catch (e: Exception) {
            Log.e("VisitedPointsRepo", "Error marking point as visited", e)
        }
    }

    fun getAllVisitedPoints(): List<VisitedPointItem> {
        return try {
            // Intentar desde cache primero
            val cachedPoints = memoryCache.values.toList()

            if (cachedPoints.isNotEmpty()) {
                cacheHitCount++
                Log.d("VisitedPointsRepo", "Cache hit: returning ${cachedPoints.size} points")
                cachedPoints.sortedByDescending { it.timestamp }
            } else {
                cacheMissCount++
                // Cargar desde storage si cache está vacío
                loadFromStorage()
            }
        } catch (e: Exception) {
            Log.e("VisitedPointsRepo", "Error getting visited points", e)
            emptyList()
        }
    }

    fun getVisitedPointsCount(): Int {
        return memoryCache.size
    }

    fun isPointVisited(pointName: String): Boolean {
        return memoryCache.values.any { it.pointName == pointName }
    }

    fun clearHistory() {
        memoryCache.clear()
        cacheAccessOrder.clear()
        sharedPrefs?.edit()?.remove(VISITED_POINTS_KEY)?.apply()
        Log.d("VisitedPointsRepo", "History cleared")
    }

    // ============= CACHE IMPLEMENTATION =============
    private fun addToMemoryCache(point: VisitedPointItem) {
        synchronized(this) {
            // Si el cache está lleno, remover el más viejo (LRU)
            if (memoryCache.size >= maxCacheSize && !memoryCache.containsKey(point.id)) {
                val oldestKey = cacheAccessOrder.removeFirstOrNull()
                oldestKey?.let { memoryCache.remove(it) }
            }

            // Añadir/actualizar en cache
            memoryCache[point.id] = point

            // Actualizar orden de acceso (mover al final = más reciente)
            cacheAccessOrder.remove(point.id)
            cacheAccessOrder.add(point.id)
        }
    }

    private fun loadCacheFromStorage() {
        if (isLoading) return
        isLoading = true

        try {
            val points = loadFromStorage()

            // Cargar los más recientes al cache
            points.take(maxCacheSize).forEach { point ->
                addToMemoryCache(point)
            }

            Log.d("VisitedPointsRepo", "Loaded ${points.size} points to cache")

        } catch (e: Exception) {
            Log.e("VisitedPointsRepo", "Error loading cache from storage", e)
        } finally {
            isLoading = false
        }
    }

    // ============= STORAGE IMPLEMENTATION =============
    private fun saveToStorage(newPoint: VisitedPointItem) {
        try {
            val currentPoints = loadFromStorage().toMutableList()

            // Verificar si ya existe (evitar duplicados)
            val existingIndex = currentPoints.indexOfFirst {
                it.pointName == newPoint.pointName
            }

            if (existingIndex != -1) {
                // Actualizar timestamp si ya existe
                currentPoints[existingIndex] = newPoint
            } else {
                // Añadir nuevo punto al inicio
                currentPoints.add(0, newPoint)
            }

            // Limitar cantidad para no sobrecargar storage
            val limitedPoints = currentPoints.take(MAX_STORED_POINTS)

            // Serializar y guardar
            val jsonString = json.encodeToString(limitedPoints)
            sharedPrefs?.edit()?.putString(VISITED_POINTS_KEY, jsonString)?.apply()

        } catch (e: Exception) {
            Log.e("VisitedPointsRepo", "Error saving to storage", e)
        }
    }

    private fun loadFromStorage(): List<VisitedPointItem> {
        return try {
            val jsonString = sharedPrefs?.getString(VISITED_POINTS_KEY, null)
            if (jsonString.isNullOrEmpty()) {
                emptyList()
            } else {
                val points: List<VisitedPointItem> = json.decodeFromString(jsonString)
                points.sortedByDescending { it.timestamp } // Más recientes primero
            }
        } catch (e: Exception) {
            Log.e("VisitedPointsRepo", "Error loading from storage", e)
            emptyList()
        }
    }

    // ============= METRICS =============
    fun getCacheMetrics(): Map<String, Any> {
        return mapOf(
            "cacheSize" to memoryCache.size,
            "maxCacheSize" to maxCacheSize,
            "cacheHits" to cacheHitCount,
            "cacheMisses" to cacheMissCount,
            "hitRate" to if (cacheHitCount + cacheMissCount > 0) {
                (cacheHitCount.toDouble() / (cacheHitCount + cacheMissCount) * 100).toInt()
            } else 0,
            "totalVisitedPoints" to memoryCache.size
        )
    }
}