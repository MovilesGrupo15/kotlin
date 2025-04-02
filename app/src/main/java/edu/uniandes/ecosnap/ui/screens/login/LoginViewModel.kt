package edu.uniandes.ecosnap.ui.screens.login

import android.util.Log
import androidx.lifecycle.ViewModel
import edu.uniandes.ecosnap.data.observer.Observer
import edu.uniandes.ecosnap.data.repository.AuthRepository
import edu.uniandes.ecosnap.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isRegistrationMode: Boolean = false,
    val error: String = ""
)

class LoginViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val authObserver = object : Observer<UserProfile?> {
        override fun onSuccess(data: UserProfile?) {
            val result = data != null
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isAuthenticated = result,
                    error = if (!result) "Autenticación fallida" else ""
                )
            }
            Log.d("LoginViewModel", "Authentication status: $result")
        }

        override fun onError(error: Throwable) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isAuthenticated = false,
                    error = "Error de autenticación: ${error.message}"
                )
            }
            Log.e("LoginViewModel", "Authentication error: ${error.message}")
        }
    }

    init {
        Log.d("LoginViewModel", "Initializing LoginViewModel...")
        AuthRepository.addObserver(authObserver)
        AuthRepository.initializeAuth()
    }

    override fun onCleared() {
        AuthRepository.removeObserver(authObserver)
        super.onCleared()
    }

    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun toggleRegistrationMode() {
        _uiState.update { it.copy(isRegistrationMode = !it.isRegistrationMode) }
    }

    fun login() {
        _uiState.update {
            it.copy(
                isLoading = true,
                error = ""
            )
        }
        AuthRepository.login(_uiState.value.email, _uiState.value.password)
    }

    fun register() {
        _uiState.update {
            it.copy(
                isLoading = true,
                error = ""
            )
        }

        val userProfile = UserProfile(
            email = _uiState.value.email,
            password = _uiState.value.password,
            userName = _uiState.value.name,
            points = 0
        )

        AuthRepository.register(userProfile)
    }

    fun resetError() {
        _uiState.update {
            it.copy(error = "")
        }
    }

    fun isInputValid(): Boolean {
        return if (_uiState.value.isRegistrationMode) {
            _uiState.value.email.isNotBlank() &&
                    _uiState.value.password.isNotBlank() &&
                    _uiState.value.name.isNotBlank()
        } else {
            _uiState.value.email.isNotBlank() &&
                    _uiState.value.password.isNotBlank()
        }
    }
}
