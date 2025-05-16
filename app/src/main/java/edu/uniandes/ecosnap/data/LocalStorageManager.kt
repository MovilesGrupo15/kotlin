package edu.uniandes.ecosnap.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.uniandes.ecosnap.domain.model.PointOfInterest
import edu.uniandes.ecosnap.domain.model.RecyclingGuideItem
import java.lang.reflect.Type

/**
 * Administra el almacenamiento local de los puntos de reciclaje cercanos
 * y la información de la guía de reciclaje
 */
class LocalStorageManager(context: Context) {

    companion object {
        private const val PREF_NAME = "ecosnap_preferences"
        private const val RECYCLING_POINTS_KEY = "recycling_points"
        private const val RECYCLING_GUIDE_KEY = "recycling_guide_items"
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

    /**
     * Guarda una lista de elementos de la guía de reciclaje en el almacenamiento local
     * @param items Lista de elementos de la guía a guardar
     */
    fun saveRecyclingGuideItems(items: List<RecyclingGuideItem>) {
        val json = gson.toJson(items)
        sharedPreferences.edit().putString(RECYCLING_GUIDE_KEY, json).apply()
    }

    /**
     * Recupera la lista de elementos de la guía de reciclaje del almacenamiento local
     * @return Lista de elementos de la guía guardados
     */
    fun getRecyclingGuideItems(): List<RecyclingGuideItem> {
        val json = sharedPreferences.getString(RECYCLING_GUIDE_KEY, null)
        return if (json != null) {
            val type: Type = object : TypeToken<List<RecyclingGuideItem>>() {}.type
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
     * Elimina todos los elementos de la guía de reciclaje del almacenamiento local
     */
    fun clearRecyclingGuideItems() {
        sharedPreferences.edit().remove(RECYCLING_GUIDE_KEY).apply()
    }

    /**
     * Actualiza un elemento específico de la guía de reciclaje en el almacenamiento local
     * @param updatedItem Elemento de la guía actualizado
     */
    fun updateRecyclingGuideItem(updatedItem: RecyclingGuideItem) {
        val items = getRecyclingGuideItems().toMutableList()

        // Encuentra el índice del elemento con el mismo ID
        val index = items.indexOfFirst { it.id == updatedItem.id }

        if (index != -1) {
            // Reemplaza el elemento existente con el actualizado
            items[index] = updatedItem
            saveRecyclingGuideItems(items)
        }
    }
}