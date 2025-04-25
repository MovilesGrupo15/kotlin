package edu.uniandes.ecosnap.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.uniandes.ecosnap.data.observer.Observer
import edu.uniandes.ecosnap.data.repository.OfferRepository
import edu.uniandes.ecosnap.data.repository.AuthRepository
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

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val offerObserver = object : Observer<Offer> {
        override fun onSuccess(data: Offer) {
            _uiState.update { currentState ->
                val updatedOffers = currentState.offers.toMutableList()
                if (!updatedOffers.contains(data)) {
                    updatedOffers.add(data)
                }
                currentState.copy(
                    offers = updatedOffers.toList(),
                    isLoading = false
                )
            }
        }

        override fun onError(error: Throwable) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "Error al cargar las ofertas: ${error.message}"
                )
            }
        }
    }

    private val userProfileObserver = object : Observer<UserProfile?> {
        override fun onSuccess(data: UserProfile?) {
            _uiState.update {
                it.copy(
                    userName = data?.userName ?: "",
                    points = data?.points ?: 0,
                    isLoading = false
                )
            }
            loadOffers()
        }

        override fun onError(error: Throwable) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "Error al cargar el perfil: ${error.message}",
                )
            }
        }
    }

    init {
        Log.d("HomeViewModel", "Initializing HomeViewModel...")
        OfferRepository.addObserver(offerObserver)
        AuthRepository.addObserver(userProfileObserver)
        AuthRepository.initializeAuth()
        loadUserProfile()
    }

    override fun onCleared() {
        OfferRepository.removeObserver(offerObserver)
        AuthRepository.removeObserver(userProfileObserver)
        super.onCleared()
    }

    fun loadUserProfile() {
        _uiState.update { it.copy(isLoading = true) }
        AuthRepository.fetch()
    }

    private fun loadOffers() {
        _uiState.update {
            it.copy(
                offers = emptyList(),
                isLoading = true,
                error = null
            )
        }
        OfferRepository.fetch()
    }

    fun signOut() {
        AuthRepository.signOut()
    }
}