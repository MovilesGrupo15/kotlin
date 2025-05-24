package edu.uniandes.ecosnap.data.repository

import android.content.Context
import android.content.SharedPreferences
import edu.uniandes.ecosnap.domain.model.ScanHistoryItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

object ScanHistoryRepository {
    private const val SCAN_HISTORY_KEY = "scan_history"
    private const val PREFS_NAME = "ecosnap_prefs"

    private var sharedPrefs: SharedPreferences? = null

    fun initialize(context: Context) {
        sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveScan(historyItem: ScanHistoryItem) {
        val currentHistory = getAllScans().toMutableList()
        currentHistory.add(0, historyItem) // Añadir al inicio

        // Limitar a últimos 50 escaneos para no sobrecargar storage
        val limitedHistory = currentHistory.take(50)

        val jsonString = Json.encodeToString(limitedHistory)
        sharedPrefs?.edit()?.putString(SCAN_HISTORY_KEY, jsonString)?.apply()
    }

    fun getAllScans(): List<ScanHistoryItem> {
        val jsonString = sharedPrefs?.getString(SCAN_HISTORY_KEY, null) ?: return emptyList()
        return try {
            Json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getScanCount(): Int = getAllScans().size

    fun clearHistory() {
        sharedPrefs?.edit()?.remove(SCAN_HISTORY_KEY)?.apply()
    }
}