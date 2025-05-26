package edu.uniandes.ecosnap.data.repository

import android.content.Context
import android.content.SharedPreferences
import edu.uniandes.ecosnap.domain.model.ScanHistoryItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

// En data/repository/ScanHistoryRepository.kt - MODIFICAR para que sea más simple:
object ScanHistoryRepository {
    private const val SCAN_HISTORY_KEY = "scan_history"
    private var context: Context? = null

    fun initialize(ctx: Context) {
        context = ctx
    }

    fun saveScan(historyItem: ScanHistoryItem) {
        val sharedPrefs = context?.getSharedPreferences("ecosnap_prefs", Context.MODE_PRIVATE)
        val currentHistory = getAllScans().toMutableList()
        currentHistory.add(0, historyItem)

        // Limitar a últimos 50 escaneos
        val limitedHistory = currentHistory.take(50)

        val jsonString = Json.encodeToString(limitedHistory)
        sharedPrefs?.edit()?.putString(SCAN_HISTORY_KEY, jsonString)?.apply()
    }

    fun getAllScans(): List<ScanHistoryItem> {
        val sharedPrefs = context?.getSharedPreferences("ecosnap_prefs", Context.MODE_PRIVATE)
        val jsonString = sharedPrefs?.getString(SCAN_HISTORY_KEY, null) ?: return emptyList()
        return try {
            Json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getScanCount(): Int = getAllScans().size
}