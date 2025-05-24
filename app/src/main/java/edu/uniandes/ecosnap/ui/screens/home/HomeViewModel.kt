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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

data class HomeUiState(
    val userName: String = "",
    val points: Int = 0,
    val offers: List<Offer> = emptyList(),
    val error: String? = null,
    val isAnonymous: Boolean = false,
    val lastRefreshTime: Long = 0L // Track refresh time
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

    // Memory management
    private val maxOffers = 20 // Limit offers to prevent memory bloat
    private val refreshCooldown = 30_000L // 30 seconds cooldown for refresh
    private val observerCleanupJobs = mutableListOf<Job>()

    // Optimized offer observer with memory limits
    private val offerObserver = object : Observer<Offer> {
        override fun onSuccess(data: Offer) {
            _isOffersLoading.value = false
            Log.d("OptimizedHomeViewModel", "Offer received: ${data.id}")

            _uiState.update { currentState ->
                val currentOffers = currentState.offers.toMutableList()

                // Remove existing offer with same ID to prevent duplicates
                val existingIndex = currentOffers.indexOfFirst { it.id == data.id }
                if (existingIndex != -1) {
                    currentOffers.removeAt(existingIndex)
                }

                // Add new offer at the beginning
                currentOffers.add(0, data)

                // Limit offers to prevent memory bloat
                val limitedOffers = if (currentOffers.size > maxOffers) {
                    Log.d("OptimizedHomeViewModel", "Trimming offers from ${currentOffers.size} to $maxOffers")
                    currentOffers.take(maxOffers)
                } else {
                    currentOffers
                }

                currentState.copy(
                    offers = limitedOffers,
                    error = null,
                    lastRefreshTime = System.currentTimeMillis()
                )
            }
        }

        override fun onError(error: Throwable) {
            Log.e("OptimizedHomeViewModel", "Offer fetch error: ${error.message}")
            _uiState.update {
                it.copy(
                    error = "Error al cargar las ofertas: ${error.message}",
                    lastRefreshTime = System.currentTimeMillis()
                )
            }
            _isOffersLoading.value = false
        }
    }

    // Optimized user profile observer
    private val userProfileObserver = object : Observer<UserProfile?> {
        override fun onSuccess(data: UserProfile?) {
            Log.d("OptimizedHomeViewModel", "User profile fetch success: ${data?.userName}")
            _uiState.update {
                it.copy(
                    userName = data?.userName ?: (if (data?.isAnonymous == true) "Usuario Anónimo" else ""),
                    points = data?.points ?: 0,
                    error = null,
                    isAnonymous = data?.isAnonymous ?: false
                )
            }
            _isProfileLoading.value = false

            // Load offers only if we don't have recent data
            val currentTime = System.currentTimeMillis()
            val lastRefresh = _uiState.value.lastRefreshTime
            if (currentTime - lastRefresh > refreshCooldown) {
                loadOffers()
            } else {
                Log.d("OptimizedHomeViewModel", "Skipping offers reload - recent data available")
            }
        }

        override fun onError(error: Throwable) {
            Log.e("OptimizedHomeViewModel", "User profile fetch error: ${error.message}")
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

    // Lightweight auth state observer
    private val authStateObserver = object : Observer<UserProfile?> {
        override fun onSuccess(data: UserProfile?) {
            if (data == null) {
                Log.d("OptimizedHomeViewModel", "Auth state changed: User is null")
                // Clear sensitive data when user logs out
                _uiState.update {
                    it.copy(
                        userName = "",
                        points = 0,
                        offers = emptyList(),
                        isAnonymous = false
                    )
                }
            } else {
                Log.d("OptimizedHomeViewModel", "Auth state changed: User is ${data.id}")
            }
        }

        override fun onError(error: Throwable) {
            Log.e("OptimizedHomeViewModel", "Auth state error: ${error.message}")
        }
    }

    init {
        Log.d("OptimizedHomeViewModel", "Initializing OptimizedHomeViewModel...")
        setupObservers()
        loadUserProfile()
    }

    private fun setupObservers() {
        // Add observers with proper cleanup tracking
        OfferRepository.addObserver(offerObserver)
        AuthRepository.addObserver(userProfileObserver)
        AuthRepository.addObserver(authStateObserver)
        AuthRepository.initializeAuth()
    }

    override fun onCleared() {
        Log.d("OptimizedHomeViewModel", "Cleaning up OptimizedHomeViewModel...")

        // Cancel any pending jobs
        observerCleanupJobs.forEach { it.cancel() }
        observerCleanupJobs.clear()

        // Remove observers
        OfferRepository.removeObserver(offerObserver)
        AuthRepository.removeObserver(userProfileObserver)
        AuthRepository.removeObserver(authStateObserver)

        // Clear state to help GC
        _uiState.value = HomeUiState()

        super.onCleared()
    }

    fun loadUserProfile() {
        if (_isProfileLoading.value) {
            Log.d("OptimizedHomeViewModel", "Profile already loading, skipping...")
            return
        }

        _isProfileLoading.value = true
        _uiState.update { it.copy(error = null) }
        AuthRepository.fetch()
    }

    private fun loadOffers() {
        if (_isOffersLoading.value) {
            Log.d("OptimizedHomeViewModel", "Offers already loading, skipping...")
            return
        }

        _isOffersLoading.value = true

        // Don't clear existing offers immediately - keep them visible while loading new ones
        _uiState.update { it.copy(error = null) }

        OfferRepository.fetch()
    }

    fun refreshOffers() {
        val currentTime = System.currentTimeMillis()
        val lastRefresh = _uiState.value.lastRefreshTime

        if (currentTime - lastRefresh < refreshCooldown) {
            Log.d("OptimizedHomeViewModel", "Refresh cooldown active, skipping...")
            return
        }

        Log.d("OptimizedHomeViewModel", "Force refreshing offers...")
        _uiState.update {
            it.copy(
                offers = emptyList(),
                lastRefreshTime = currentTime
            )
        }
        loadOffers()
    }

    fun signOut() {
        // Clear data before signing out
        _uiState.update {
            it.copy(
                userName = "",
                points = 0,
                offers = emptyList(),
                error = null,
                isAnonymous = false
            )
        }
        AuthRepository.signOut()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}