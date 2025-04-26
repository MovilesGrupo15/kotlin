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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val userName: String = "",
    val points: Int = 0,
    val offers: List<Offer> = emptyList(),
    val error: String? = null,
    val isAnonymous: Boolean = false
)

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _isProfileLoading = MutableStateFlow(false)
    private val _isOffersLoading = MutableStateFlow(false)

    val isLoading: StateFlow<Boolean> = combine(_isProfileLoading, _isOffersLoading) { isProfile, isOffers ->
        isProfile || isOffers
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        true
    )

    private val offerObserver = object : Observer<Offer> {
        override fun onSuccess(data: Offer) {
            _isOffersLoading.value = false
            Log.d("HomeViewModel", "Offer received: ${data.id}")
            _uiState.update { currentState ->
                val updatedOffers = currentState.offers.toMutableList()
                if (!updatedOffers.contains(data)) {
                    updatedOffers.add(data)
                }
                currentState.copy(offers = updatedOffers.toList(), error = null)
            }
        }

        override fun onError(error: Throwable) {
            Log.e("HomeViewModel", "Offer fetch error: ${error.message}")
            _uiState.update { it.copy(error = "Error al cargar las ofertas: ${error.message}") }
            _isOffersLoading.value = false
        }
    }

    private val userProfileObserver = object : Observer<UserProfile?> {
        override fun onSuccess(data: UserProfile?) {
            Log.d("HomeViewModel", "User profile fetch success: ${data?.userName}")
            _uiState.update {
                it.copy(
                    userName = data?.userName ?: (if (data?.isAnonymous == true) "Usuario Anónimo" else ""),
                    points = data?.points ?: 0,
                    error = null,
                    isAnonymous = data?.isAnonymous ?: false
                )
            }
            _isProfileLoading.value = false

            loadOffers()
        }

        override fun onError(error: Throwable) {
            Log.e("HomeViewModel", "User profile fetch error: ${error.message}")
            _uiState.update {
                it.copy(
                    userName = "Error",
                    points = 0,
                    error = "Error al cargar el perfil: ${error.message}",
                    isAnonymous = AuthRepository.getCurrentUser()?.isAnonymous ?: false
                )
            }
            _isProfileLoading.value = false
            _isOffersLoading.value = false
        }
    }

    private val authStateObserver = object : Observer<UserProfile?> {
        override fun onSuccess(data: UserProfile?) {
            if (data == null) {
                Log.d("HomeViewModel", "Auth state changed: User is null")
            } else {
                Log.d("HomeViewModel", "Auth state changed: User is ${data.id}")
            }
        }

        override fun onError(error: Throwable) {
            Log.e("HomeViewModel", "Auth state error: ${error.message}")
        }
    }


    init {
        Log.d("HomeViewModel", "Initializing HomeViewModel...")
        OfferRepository.addObserver(offerObserver)
        AuthRepository.addObserver(userProfileObserver)
        AuthRepository.addObserver(authStateObserver)
        AuthRepository.initializeAuth()
        loadUserProfile()
    }

    override fun onCleared() {
        OfferRepository.removeObserver(offerObserver)
        AuthRepository.removeObserver(userProfileObserver)
        AuthRepository.removeObserver(authStateObserver)
        super.onCleared()
    }

    fun loadUserProfile() {
        _isProfileLoading.value = true
        _uiState.update { it.copy(error = null) }
        AuthRepository.fetch()
    }

    private fun loadOffers() {
        _isOffersLoading.value = true
        _uiState.update {
            it.copy(
                offers = emptyList(),
                error = null
            )
        }
        OfferRepository.fetch()
    }

    fun signOut() {
        AuthRepository.signOut()
    }
}