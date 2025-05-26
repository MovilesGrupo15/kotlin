package edu.uniandes.ecosnap.ui.screens.history

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.uniandes.ecosnap.domain.model.ScanHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.concurrent.ConcurrentHashMap

data class ScanHistoryUiState(
    val scanHistory: List<ScanHistoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val totalScans: Int = 0,
    val cacheHits: Int = 0,
    val cachedItems: Int = 0
)

class ScanHistoryViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ScanHistoryUiState())
    val uiState: StateFlow<ScanHistoryUiState> = _uiState.asStateFlow()

    // ============= ESTRATEGIA (b): LOCAL STORAGE =============
    private var sharedPrefs: SharedPreferences? = null
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val PREFS_NAME = "ecosnap_scan_history"
        private const val SCAN_HISTORY_KEY = "scan_history_data"
        private const val MAX_STORED_SCANS = 100 // Límite para no sobrecargar storage
    }

    // ============= ESTRATEGIA (c): CACHING =============
    private val memoryCache = ConcurrentHashMap<String, ScanHistoryItem>()
    private val cacheAccessOrder = mutableListOf<String>() // Para LRU
    private var cacheHitCount = 0
    private var cacheMissCount = 0
    private val maxCacheSize = 50 // Máximo elementos en memoria

    fun initialize(context: Context) {
        sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.d("ScanHistoryVM", "Initialized with Local Storage: SharedPreferences")
    }

    // ============= MULTI-THREADING CON COROUTINES =============
    fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Background thread para I/O pesado
                val scans = withContext(Dispatchers.IO) {
                    loadScansFromStorage()
                }

                // Actualizar cache en background
                withContext(Dispatchers.Default) {
                    updateMemoryCache(scans)
                }

                // Main thread para UI
                _uiState.value = _uiState.value.copy(
                    scanHistory = scans,
                    isLoading = false,
                    totalScans = scans.size,
                    cacheHits = cacheHitCount,
                    cachedItems = memoryCache.size
                )

                Log.d("ScanHistoryVM", "Loaded ${scans.size} scans from storage")

            } catch (e: Exception) {
                Log.e("ScanHistoryVM", "Error loading history", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error cargando historial: ${e.message}"
                )
            }
        }
    }

    fun refreshHistory() {
        // Limpiar cache para forzar reload
        memoryCache.clear()
        cacheAccessOrder.clear()
        loadHistory()
    }

    fun addScanToHistory(scan: ScanHistoryItem) {
        viewModelScope.launch {
            try {
                // Background thread para operaciones de storage
                withContext(Dispatchers.IO) {
                    saveScanToStorage(scan)
                }

                // Update cache inmediatamente
                addToMemoryCache(scan)

                // Refresh UI
                loadHistory()

            } catch (e: Exception) {
                Log.e("ScanHistoryVM", "Error adding scan to history", e)
            }
        }
    }

    // ============= LOCAL STORAGE IMPLEMENTATION =============
    private suspend fun loadScansFromStorage(): List<ScanHistoryItem> {
        return withContext(Dispatchers.IO) {
            try {
                val jsonString = sharedPrefs?.getString(SCAN_HISTORY_KEY, null)
                if (jsonString.isNullOrEmpty()) {
                    emptyList()
                } else {
                    val scans: List<ScanHistoryItem> = json.decodeFromString(jsonString)
                    Log.d("ScanHistoryVM", "Loaded ${scans.size} scans from SharedPreferences")
                    scans.sortedByDescending { it.timestamp } // Más recientes primero
                }
            } catch (e: Exception) {
                Log.e("ScanHistoryVM", "Error deserializing scans from SharedPreferences", e)
                emptyList()
            }
        }
    }

    private suspend fun saveScanToStorage(newScan: ScanHistoryItem) {
        withContext(Dispatchers.IO) {
            try {
                val currentScans = loadScansFromStorage().toMutableList()

                // Añadir nuevo scan al inicio
                currentScans.add(0, newScan)

                // Limitar cantidad para no sobrecargar storage
                val limitedScans = currentScans.take(MAX_STORED_SCANS)

                // Serializar y guardar
                val jsonString = json.encodeToString(limitedScans)
                sharedPrefs?.edit()?.putString(SCAN_HISTORY_KEY, jsonString)?.apply()

                Log.d("ScanHistoryVM", "Saved scan to SharedPreferences. Total: ${limitedScans.size}")

            } catch (e: Exception) {
                Log.e("ScanHistoryVM", "Error saving scan to SharedPreferences", e)
            }
        }
    }

    // ============= MEMORY CACHE IMPLEMENTATION (LRU) =============
    private fun updateMemoryCache(scans: List<ScanHistoryItem>) {
        // Limpiar cache existente
        memoryCache.clear()
        cacheAccessOrder.clear()

        // Añadir los scans más recientes al cache
        scans.take(maxCacheSize).forEach { scan ->
            addToMemoryCache(scan)
        }

        Log.d("ScanHistoryVM", "Updated memory cache with ${memoryCache.size} items")
    }

    private fun addToMemoryCache(scan: ScanHistoryItem) {
        synchronized(this) {
            // Si el cache está lleno, remover el más viejo (LRU)
            if (memoryCache.size >= maxCacheSize && !memoryCache.containsKey(scan.id)) {
                val oldestKey = cacheAccessOrder.removeFirstOrNull()
                oldestKey?.let { memoryCache.remove(it) }
            }

            // Añadir/actualizar en cache
            memoryCache[scan.id] = scan

            // Actualizar orden de acceso (mover al final = más reciente)
            cacheAccessOrder.remove(scan.id)
            cacheAccessOrder.add(scan.id)
        }
    }

    fun getScanFromCache(scanId: String): ScanHistoryItem? {
        return synchronized(this) {
            val scan = memoryCache[scanId]
            if (scan != null) {
                // Cache hit - mover al final (más reciente)
                cacheAccessOrder.remove(scanId)
                cacheAccessOrder.add(scanId)
                cacheHitCount++

                // Actualizar UI state con nuevas métricas
                _uiState.value = _uiState.value.copy(
                    cacheHits = cacheHitCount,
                    cachedItems = memoryCache.size
                )
            } else {
                cacheMissCount++
            }
            scan
        }
    }

    // ============= MEMORY MANAGEMENT =============
    override fun onCleared() {
        super.onCleared()
        // Limpiar cache al destruir ViewModel
        memoryCache.clear()
        cacheAccessOrder.clear()
        Log.d("ScanHistoryVM", "ViewModel cleared. Cache cleaned.")
    }

    // ============= DEBUGGING/METRICS =============
    fun getCacheMetrics(): Map<String, Any> {
        return mapOf(
            "cacheSize" to memoryCache.size,
            "maxCacheSize" to maxCacheSize,
            "cacheHits" to cacheHitCount,
            "cacheMisses" to cacheMissCount,
            "hitRate" to if (cacheHitCount + cacheMissCount > 0) {
                (cacheHitCount.toDouble() / (cacheHitCount + cacheMissCount) * 100).toInt()
            } else 0
        )
    }
}