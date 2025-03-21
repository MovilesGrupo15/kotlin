package edu.uniandes.ecosnap.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import edu.uniandes.ecosnap.data.observer.Observer
import edu.uniandes.ecosnap.data.repository.OfferRepository
import edu.uniandes.ecosnap.data.repository.UserRepository
import edu.uniandes.ecosnap.domain.model.Offer
import edu.uniandes.ecosnap.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
                updatedOffers.add(data)
                currentState.copy(
                    offers = updatedOffers,
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
    private val userProfileObserver = object : Observer<UserProfile> {
        override fun onSuccess(data: UserProfile) {
            _uiState.update {
                it.copy(
                    userName = data.userName,
                    points = data.points,
                    isLoading = false
                )
            }
        }

        override fun onError(error: Throwable) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = "Error al cargar el perfil: ${error.message}"
                )
            }
        }

    }

    init {
        Log.d("HomeViewModel", "Initializing HomeViewModel...")
        OfferRepository.addObserver(offerObserver)
        UserRepository.addObserver(userProfileObserver)
        loadUserProfile()
        loadOffers()
    }

    override fun onCleared() {
        OfferRepository.removeObserver(offerObserver)
        super.onCleared()
    }

    private fun loadUserProfile() {
        UserRepository.fetch()
    }

    private fun loadOffers() {
        _uiState.update {
            it.copy(
                offers = emptyList(),
                isLoading = true
            )
        }
        OfferRepository.fetch()
    }
}