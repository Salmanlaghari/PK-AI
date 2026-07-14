package com.salmanlaghari.pkai.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salmanlaghari.pkai.data.local.datastore.UserSession
import com.salmanlaghari.pkai.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    object Idle : LoginUiState
    object Loading : LoginUiState
    data class Success(val session: UserSession) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    fun loginWithGoogle(idToken: String, displayName: String?, email: String?, photoUrl: String?) {
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            val result = authRepository.loginWithGoogle(idToken, displayName, email, photoUrl)
            result.onSuccess { session ->
                _uiState.value = LoginUiState.Success(session)
            }.onFailure { exception ->
                _uiState.value = LoginUiState.Error(exception.message ?: "Google Sign-In failed")
            }
        }
    }

    fun loginAsGuest() {
        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            val result = authRepository.loginAsGuest()
            result.onSuccess { session ->
                _uiState.value = LoginUiState.Success(session)
            }.onFailure { exception ->
                _uiState.value = LoginUiState.Error(exception.message ?: "Guest login failed")
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}
