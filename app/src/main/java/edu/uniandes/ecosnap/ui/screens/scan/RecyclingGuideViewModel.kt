package edu.uniandes.ecosnap.ui.screens.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.uniandes.ecosnap.data.LocalStorageManager
import edu.uniandes.ecosnap.data.observer.Observer
import edu.uniandes.ecosnap.data.repository.RecyclingGuideRepository
import edu.uniandes.ecosnap.domain.model.RecyclingGuideItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecyclingGuideUiState(
    val guideItems: List<RecyclingGuideItem> = emptyList(),
    val filteredItems: List<RecyclingGuideItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOfflineMode: Boolean = false,
    val selectedCategory: String? = null,
    val categories: List<String> = emptyList()
)

class RecyclingGuideViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(RecyclingGuideUiState())
    val uiState: StateFlow<RecyclingGuideUiState> = _uiState.asStateFlow()

    private val localStorageManager = LocalStorageManager(application.applicationContext)

    private val guideObserver = object : Observer<RecyclingGuideItem> {
        override fun onSuccess(data: RecyclingGuideItem) {
            _uiState.update { currentState ->
                val updatedGuide = currentState.guideItems.toMutableList()

                // Evitar duplicados
                val existingIndex = updatedGuide.indexOfFirst { it.id == data.id }
                if (existingIndex != -1) {
                    updatedGuide[existingIndex] = data
                } else {
                    updatedGuide.add(data)
                }

                // Extraer categorías únicas
                val categories = updatedGuide.map { it.category }.distinct().sorted()

                // Aplicar filtro actual si existe
                val filteredItems = if (currentState.searchQuery.isNotEmpty()) {
                    filterItems(updatedGuide, currentState.searchQuery, currentState.selectedCategory)
                } else if (currentState.selectedCategory != null) {
                    updatedGuide.filter { it.category == currentState.selectedCategory }
                } else {
                    updatedGuide
                }

                currentState.copy(
                    guideItems = updatedGuide,
                    filteredItems = filteredItems,
                    categories = categories,
                    isLoading = false,
                    isOfflineMode = false
                )
            }
        }

        override fun onError(error: Throwable) {
            // Si hay un error cargando desde el repositorio, intentar cargar del storage local
            val localItems = localStorageManager.getRecyclingGuideItems()

            if (localItems.isNotEmpty()) {
                // Extraer categorías únicas
                val categories = localItems.map { it.category }.distinct().sorted()

                _uiState.update {
                    it.copy(
                        guideItems = localItems,
                        filteredItems = localItems,
                        categories = categories,
                        isLoading = false,
                        isOfflineMode = true
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error loading recycling guide: ${error.message}"
                    )
                }
            }
        }
    }

    init {
        // Inicializar el repositorio
        RecyclingGuideRepository.initialize(application.applicationContext)
        RecyclingGuideRepository.addObserver(guideObserver)
        loadRecyclingGuide()
    }

    override fun onCleared() {
        RecyclingGuideRepository.removeObserver(guideObserver)
        super.onCleared()
    }

    fun loadRecyclingGuide() {
        _uiState.update {
            it.copy(
                isLoading = true
            )
        }

        // Cargar datos del repositorio (que intentará backend y usará local como fallback)
        RecyclingGuideRepository.fetch()
    }

    fun refreshFromNetwork() {
        _uiState.update {
            it.copy(
                isLoading = true
            )
        }
        // Forzar actualización desde el backend
        RecyclingGuideRepository.forceUpdate()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { currentState ->
            val filteredItems = filterItems(currentState.guideItems, query, currentState.selectedCategory)
            currentState.copy(
                searchQuery = query,
                filteredItems = filteredItems
            )
        }
    }

    fun selectCategory(category: String?) {
        _uiState.update { currentState ->
            val filteredItems = filterItems(currentState.guideItems, currentState.searchQuery, category)
            currentState.copy(
                selectedCategory = category,
                filteredItems = filteredItems
            )
        }
    }

    private fun filterItems(
        items: List<RecyclingGuideItem>,
        query: String,
        category: String?
    ): List<RecyclingGuideItem> {
        val byQuery = if (query.isEmpty()) items else items.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true) ||
                    it.wasteType.contains(query, ignoreCase = true)
        }

        return if (category == null) byQuery else byQuery.filter { it.category == category }
    }
}