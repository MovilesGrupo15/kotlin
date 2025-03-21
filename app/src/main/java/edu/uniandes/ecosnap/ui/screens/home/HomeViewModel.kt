package edu.uniandes.ecosnap.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.uniandes.ecosnap.data.repository.OfferRepository
import edu.uniandes.ecosnap.data.repository.UserRepository
import edu.uniandes.ecosnap.domain.model.Offer
import edu.uniandes.ecosnap.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val userName: String = "",
    val points: Int = 0,
    val offers: List<Offer> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val offerRepository: OfferRepository = OfferRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        Log.d("HomeViewModel", "Initializing HomeViewModel...")
        loadUserProfile()
        loadOffers()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                userRepository.getUserProfile().collect { userProfile ->
                    _uiState.update {
                        it.copy(
                            userName = userProfile.userName,
                            points = userProfile.points,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error al cargar el perfil: ${e.message}"
                    )
                }
            }
        }
    }

    private fun loadOffers() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                offerRepository.getOffers().collect { offers ->
                    _uiState.update {
                        it.copy(
                            offers = offers,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Error al cargar las ofertas: ${e.message}"
                    )
                }
            }
        }
    }
}