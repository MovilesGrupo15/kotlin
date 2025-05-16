package edu.uniandes.ecosnap.ui.screens.scan

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.uniandes.ecosnap.data.LocalStorageManager
import edu.uniandes.ecosnap.data.observer.Observer
import edu.uniandes.ecosnap.data.repository.PointOfInterestRepository
import edu.uniandes.ecosnap.domain.model.PointOfInterest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class NearbyPointsUiState(
    val pointsOfInterest: List<PointOfInterest> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOfflineMode: Boolean = false
)

class NearbyPointsViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(NearbyPointsUiState())
    val uiState: StateFlow<NearbyPointsUiState> = _uiState.asStateFlow()

    private val localStorageManager = LocalStorageManager(application.applicationContext)

    // Ubicación actual del usuario (puede ser actualizada desde la UI)
    private var userLatitude: Double = 4.6097  // Ubicación predeterminada (Bogotá)
    private var userLongitude: Double = -74.0817

    // Límite de puntos a mostrar para mejorar el rendimiento
    private val MAX_POINTS_TO_DISPLAY = 30

    private val pointsOfInterestObserver = object : Observer<PointOfInterest> {
        override fun onSuccess(data: PointOfInterest) {
            _uiState.update { currentState ->
                val updatedPoints = currentState.pointsOfInterest.toMutableList()
                updatedPoints.add(data)

                // Guarda todos los puntos en el almacenamiento local
                viewModelScope.launch {
                    localStorageManager.clearRecyclingPoints()
                    localStorageManager.saveRecyclingPoints(updatedPoints)
                }

                // Pero muestra solo un número limitado ordenados por distancia
                val limitedPoints = limitPointsByDistance(updatedPoints)

                currentState.copy(
                    pointsOfInterest = limitedPoints,
                    isLoading = false,
                    isOfflineMode = false
                )
            }
        }

        override fun onError(error: Throwable) {
            // Si hay un error en la carga desde el repositorio, intenta cargar desde el almacenamiento local
            val localPoints = localStorageManager.getRecyclingPoints()

            if (localPoints.isNotEmpty()) {
                // Limita los puntos para mejorar el rendimiento
                val limitedPoints = limitPointsByDistance(localPoints)

                _uiState.update {
                    it.copy(
                        pointsOfInterest = limitedPoints,
                        isLoading = false,
                        isOfflineMode = true
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error loading points of interest: ${error.message}"
                    )
                }
            }
        }
    }

    init {
        PointOfInterestRepository.addObserver(pointsOfInterestObserver)
        loadPointsOfInterest()
    }

    override fun onCleared() {
        PointOfInterestRepository.removeObserver(pointsOfInterestObserver)
        super.onCleared()
    }

    fun loadPointsOfInterest() {
        _uiState.update {
            it.copy(
                isLoading = true
            )
        }

        // Primero intenta cargar desde el almacenamiento local
        val localPoints = localStorageManager.getRecyclingPoints()

        if (localPoints.isNotEmpty()) {
            // Limita los puntos para mejorar el rendimiento
            val limitedPoints = limitPointsByDistance(localPoints)

            _uiState.update {
                it.copy(
                    pointsOfInterest = limitedPoints,
                    isLoading = false,
                    isOfflineMode = true
                )
            }
        }

        // Luego intenta obtener los datos actualizados del repositorio
        PointOfInterestRepository.fetch()
    }

    fun refreshFromNetwork() {
        _uiState.update {
            it.copy(
                isLoading = true
            )
        }
        PointOfInterestRepository.fetch()
    }

    // Actualiza la ubicación del usuario
    fun updateUserLocation(latitude: Double, longitude: Double) {
        userLatitude = latitude
        userLongitude = longitude

        // Reordenar los puntos según la nueva ubicación
        _uiState.update { currentState ->
            val sortedPoints = limitPointsByDistance(currentState.pointsOfInterest)
            currentState.copy(pointsOfInterest = sortedPoints)
        }
    }

    // Limita los puntos mostrados ordenándolos por distancia
    private fun limitPointsByDistance(points: List<PointOfInterest>): List<PointOfInterest> {
        if (points.isEmpty()) return emptyList()

        return points
            .sortedBy { point ->
                calculateDistance(
                    userLatitude, userLongitude,
                    point.latitude, point.longitude
                )
            }
            .take(MAX_POINTS_TO_DISPLAY)
    }

    // Calcula la distancia entre dos puntos geográficos
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Radio de la Tierra en km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}