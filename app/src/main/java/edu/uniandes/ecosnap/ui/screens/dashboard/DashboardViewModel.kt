package edu.uniandes.ecosnap.ui.screens.dashboard

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.uniandes.ecosnap.data.repository.ScanHistoryRepository
import edu.uniandes.ecosnap.data.repository.VisitedPointsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

data class DashboardUiState(
    val isLoading: Boolean = true,
    val dashboardStats: DashboardStats? = null,
    val serverStats: Map<String, Any> = emptyMap(),
    val processorMetrics: Map<String, Any> = emptyMap(),
    val error: String? = null,
    val loadingProgress: Int = 0,
    val lastUpdateTime: Long = 0L
)

class DashboardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return

        // Initialize repositories on IO thread
        viewModelScope.launch(Dispatchers.IO) {
            ScanHistoryRepository.initialize(context)
            VisitedPointsRepository.initialize(context)
            isInitialized = true

            // Start loading data
            loadDashboardData()
        }
    }

    // ============= MULTITHREADING IMPLEMENTATION =============

    fun loadDashboardData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null, loadingProgress = 0)

                Log.d("DashboardVM", "Starting parallel data loading...")

                // STEP 1: Load basic data from repositories (I/O operations)
                updateProgress(20)
                val scanHistoryDeferred = async(Dispatchers.IO) {
                    Log.d("DashboardVM", "Loading scan history on IO thread")
                    ScanHistoryRepository.getAllScans()
                }

                val visitedPointsDeferred = async(Dispatchers.IO) {
                    Log.d("DashboardVM", "Loading visited points on IO thread")
                    VisitedPointsRepository.getAllVisitedPoints()
                }

                // STEP 2: Load server statistics in parallel (Network operations)
                updateProgress(40)
                val serverStatsDeferred = async(Dispatchers.IO) {
                    Log.d("DashboardVM", "Fetching server stats on IO thread")
                    DashboardDataProcessor.fetchServerStatistics()
                }

                // STEP 3: Wait for I/O operations to complete
                val scanHistory = scanHistoryDeferred.await()
                val visitedPoints = visitedPointsDeferred.await()

                updateProgress(60)
                Log.d("DashboardVM", "Data loaded: ${scanHistory.size} scans, ${visitedPoints.size} visits")

                // STEP 4: Heavy calculations on CPU threads (Dispatchers.Default)
                val statsDeferred = async(Dispatchers.Default) {
                    Log.d("DashboardVM", "Processing statistics on CPU thread")
                    DashboardDataProcessor.processCompleteStatistics(scanHistory, visitedPoints)
                }

                val metricsDeferred = async(Dispatchers.Default) {
                    Log.d("DashboardVM", "Calculating processor metrics on CPU thread")
                    DashboardDataProcessor.getProcessorMetrics()
                }

                updateProgress(80)

                // STEP 5: Wait for all operations to complete
                val results = awaitAll(statsDeferred, serverStatsDeferred, metricsDeferred)
                val dashboardStats = results[0] as DashboardStats
                val serverStats = results[1] as Map<String, Any>
                val processorMetrics = results[2] as Map<String, Any>

                updateProgress(100)

                // STEP 6: Update UI on Main thread
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        dashboardStats = dashboardStats,
                        serverStats = serverStats,
                        processorMetrics = processorMetrics,
                        lastUpdateTime = System.currentTimeMillis(),
                        loadingProgress = 100
                    )

                    Log.d("DashboardVM", "Dashboard data loaded successfully with multithreading")
                }

            } catch (e: Exception) {
                Log.e("DashboardVM", "Error loading dashboard data", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error cargando estadísticas: ${e.message}",
                    loadingProgress = 0
                )
            }
        }
    }

    fun refreshData() {
        Log.d("DashboardVM", "Refreshing dashboard data...")
        loadDashboardData()
    }

    // Background data refresh without showing loading
    fun backgroundRefresh() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("DashboardVM", "Background refresh started")

                // Quick refresh of local data
                val scanHistory = ScanHistoryRepository.getAllScans()
                val visitedPoints = VisitedPointsRepository.getAllVisitedPoints()

                // Quick stats calculation
                val quickStats = async(Dispatchers.Default) {
                    DashboardDataProcessor.processCompleteStatistics(scanHistory, visitedPoints)
                }

                val stats = quickStats.await()

                // Update UI with fresh data
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        dashboardStats = stats,
                        lastUpdateTime = System.currentTimeMillis()
                    )
                }

                Log.d("DashboardVM", "Background refresh completed")

            } catch (e: Exception) {
                Log.e("DashboardVM", "Background refresh failed", e)
            }
        }
    }

    private suspend fun updateProgress(progress: Int) {
        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(loadingProgress = progress)
        }
        // Small delay to show progress visually
        kotlinx.coroutines.delay(200)
    }

    // ============= MEMORY MANAGEMENT =============

    override fun onCleared() {
        Log.d("DashboardVM", "ViewModel cleared - canceling background operations")
        super.onCleared()
    }

    // ============= UTILITY METHODS =============

    fun getThreadingInfo(): Map<String, Any> {
        return mapOf(
            "activeThreads" to Thread.activeCount(),
            "currentThread" to Thread.currentThread().name,
            "isMainThread" to (Thread.currentThread() == android.os.Looper.getMainLooper().thread)
        )
    }

    fun getPerformanceMetrics(): Map<String, Any> {
        val state = _uiState.value
        return mapOf(
            "lastUpdateTime" to state.lastUpdateTime,
            "isLoading" to state.isLoading,
            "loadingProgress" to state.loadingProgress,
            "hasError" to (state.error != null),
            "dataLoaded" to (state.dashboardStats != null)
        )
    }
}