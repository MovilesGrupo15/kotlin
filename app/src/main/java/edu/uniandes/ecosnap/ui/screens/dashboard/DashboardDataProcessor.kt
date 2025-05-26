package edu.uniandes.ecosnap.ui.screens.dashboard

import android.util.Log
import edu.uniandes.ecosnap.domain.model.ScanHistoryItem
import edu.uniandes.ecosnap.domain.model.VisitedPointItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class WasteTypeStats(
    val type: String,
    val count: Int,
    val percentage: Float,
    val avgConfidence: Float
)

data class ActivityTrend(
    val date: String,
    val scanCount: Int,
    val visitCount: Int
)

data class DashboardStats(
    val totalScans: Int,
    val totalVisits: Int,
    val avgConfidence: Float,
    val mostScannedType: String,
    val wasteTypeStats: List<WasteTypeStats>,
    val weeklyTrends: List<ActivityTrend>,
    val thisWeekScans: Int,
    val lastWeekScans: Int,
    val improvementPercentage: Float
)

object DashboardDataProcessor {

    // ============= MULTITHREADING STRATEGY =============
    // Heavy calculations in Dispatchers.Default (CPU intensive)
    // I/O operations in Dispatchers.IO

    suspend fun processCompleteStatistics(
        scanHistory: List<ScanHistoryItem>,
        visitedPoints: List<VisitedPointItem>
    ): DashboardStats = withContext(Dispatchers.Default) {
        Log.d("DashboardProcessor", "Starting heavy calculations on background thread")

        val stats = calculateBasicStats(scanHistory, visitedPoints)
        val wasteStats = calculateWasteTypeStatistics(scanHistory)
        val trends = calculateWeeklyTrends(scanHistory, visitedPoints)
        val improvement = calculateImprovementPercentage(scanHistory)

        DashboardStats(
            totalScans = stats.first,
            totalVisits = stats.second,
            avgConfidence = stats.third,
            mostScannedType = wasteStats.maxByOrNull { it.count }?.type ?: "N/A",
            wasteTypeStats = wasteStats,
            weeklyTrends = trends,
            thisWeekScans = improvement.first,
            lastWeekScans = improvement.second,
            improvementPercentage = improvement.third
        )
    }

    // CPU intensive calculation - runs on Dispatchers.Default
    private suspend fun calculateBasicStats(
        scans: List<ScanHistoryItem>,
        visits: List<VisitedPointItem>
    ): Triple<Int, Int, Float> = withContext(Dispatchers.Default) {

        val totalScans = scans.size
        val totalVisits = visits.size

        // Simulate heavy calculation with some processing time
        val avgConfidence = if (scans.isNotEmpty()) {
            scans.map { it.confidence }.average().toFloat()
        } else 0f

        Log.d("DashboardProcessor", "Basic stats calculated: $totalScans scans, $totalVisits visits")
        Triple(totalScans, totalVisits, avgConfidence)
    }

    // Complex data aggregation - CPU intensive
    private suspend fun calculateWasteTypeStatistics(
        scans: List<ScanHistoryItem>
    ): List<WasteTypeStats> = withContext(Dispatchers.Default) {

        if (scans.isEmpty()) return@withContext emptyList()

        // Group by waste type and calculate statistics
        val groupedByType = scans.groupBy { it.detectedType }
        val totalScans = scans.size

        val stats = groupedByType.map { (type, scanList) ->
            val count = scanList.size
            val percentage = (count.toFloat() / totalScans * 100)
            val avgConfidence = scanList.map { it.confidence }.average().toFloat()

            WasteTypeStats(
                type = type.replaceFirstChar { it.uppercase() },
                count = count,
                percentage = percentage,
                avgConfidence = avgConfidence
            )
        }.sortedByDescending { it.count }

        Log.d("DashboardProcessor", "Waste type stats calculated for ${stats.size} types")
        stats
    }

    // Time-based analysis - CPU intensive
    private suspend fun calculateWeeklyTrends(
        scans: List<ScanHistoryItem>,
        visits: List<VisitedPointItem>
    ): List<ActivityTrend> = withContext(Dispatchers.Default) {

        val currentTime = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L
        val trends = mutableListOf<ActivityTrend>()

        // Calculate last 7 days
        for (i in 6 downTo 0) {
            val dayStart = currentTime - (i * oneDay)
            val dayEnd = dayStart + oneDay

            val dayScans = scans.count {
                it.timestamp >= dayStart && it.timestamp < dayEnd
            }
            val dayVisits = visits.count {
                it.timestamp >= dayStart && it.timestamp < dayEnd
            }

            val dateFormat = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
            val dateStr = dateFormat.format(java.util.Date(dayStart))

            trends.add(ActivityTrend(dateStr, dayScans, dayVisits))
        }

        Log.d("DashboardProcessor", "Weekly trends calculated for ${trends.size} days")
        trends
    }

    // Performance comparison calculation
    private suspend fun calculateImprovementPercentage(
        scans: List<ScanHistoryItem>
    ): Triple<Int, Int, Float> = withContext(Dispatchers.Default) {

        val currentTime = System.currentTimeMillis()
        val oneWeek = 7 * 24 * 60 * 60 * 1000L

        val thisWeekStart = currentTime - oneWeek
        val lastWeekStart = currentTime - (2 * oneWeek)

        val thisWeekScans = scans.count { it.timestamp >= thisWeekStart }
        val lastWeekScans = scans.count {
            it.timestamp >= lastWeekStart && it.timestamp < thisWeekStart
        }

        val improvement = if (lastWeekScans > 0) {
            ((thisWeekScans - lastWeekScans).toFloat() / lastWeekScans * 100)
        } else if (thisWeekScans > 0) {
            100f
        } else {
            0f
        }

        Log.d("DashboardProcessor", "Improvement calculated: $improvement%")
        Triple(thisWeekScans, lastWeekScans, improvement)
    }

    // Simulate network-heavy operation
    suspend fun fetchServerStatistics(): Map<String, Any> = withContext(Dispatchers.IO) {
        Log.d("DashboardProcessor", "Simulating server statistics fetch...")

        // Simulate network delay
        kotlinx.coroutines.delay(1000)

        // Return mock server data
        mapOf(
            "globalScans" to (15000..50000).random(),
            "activeUsers" to (500..2000).random(),
            "topWasteType" to listOf("plastic", "paper", "glass", "metal").random(),
            "avgUserScans" to (10..100).random()
        )
    }

    // Performance metrics for the processor itself
    fun getProcessorMetrics(): Map<String, Any> {
        return mapOf(
            "availableProcessors" to Runtime.getRuntime().availableProcessors(),
            "maxMemory" to (Runtime.getRuntime().maxMemory() / 1024 / 1024), // MB
            "freeMemory" to (Runtime.getRuntime().freeMemory() / 1024 / 1024), // MB
            "usedMemory" to ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024) // MB
        )
    }
}