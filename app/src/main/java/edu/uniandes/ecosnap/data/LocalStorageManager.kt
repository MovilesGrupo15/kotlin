package edu.uniandes.ecosnap.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.uniandes.ecosnap.domain.model.PointOfInterest
import java.lang.reflect.Type

/**
 * Administra el almacenamiento local de los puntos de reciclaje cercanos
 */
class LocalStorageManager(context: Context) {

    companion object {
        private const val PREF_NAME = "ecosnap_preferences"
        private const val RECYCLING_POINTS_KEY = "recycling_points"
    }

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    /**
     * Guarda una lista de puntos de reciclaje en el almacenamiento local
     * @param points Lista de puntos de interés a guardar
     */
    fun saveRecyclingPoints(points: List<PointOfInterest>) {
        val json = gson.toJson(points)
        sharedPreferences.edit().putString(RECYCLING_POINTS_KEY, json).apply()
    }

    /**
     * Recupera la lista de puntos de reciclaje del almacenamiento local
     * @return Lista de puntos de interés guardados
     */
    fun getRecyclingPoints(): List<PointOfInterest> {
        val json = sharedPreferences.getString(RECYCLING_POINTS_KEY, null)
        return if (json != null) {
            val type: Type = object : TypeToken<List<PointOfInterest>>() {}.type
            try {
                gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    /**
     * Elimina todos los puntos de reciclaje del almacenamiento local
     */
    fun clearRecyclingPoints() {
        sharedPreferences.edit().remove(RECYCLING_POINTS_KEY).apply()
    }

    /**
     * Actualiza un punto de reciclaje específico en el almacenamiento local
     * @param updatedPoint Punto de interés actualizado
     */
    fun updateRecyclingPoint(updatedPoint: PointOfInterest) {
        val points = getRecyclingPoints().toMutableList()

        // Encuentra el índice del punto con el mismo ID
        val index = points.indexOfFirst { it.id == updatedPoint.id }

        if (index != -1) {
            // Reemplaza el punto existente con el actualizado
            points[index] = updatedPoint
            saveRecyclingPoints(points)
        }
    }
}