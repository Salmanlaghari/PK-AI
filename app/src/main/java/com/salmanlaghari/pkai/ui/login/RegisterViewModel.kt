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

sealed interface RegisterUiState {
    object Idle : RegisterUiState
    object Loading : RegisterUiState
    data class Success(val session: UserSession) : RegisterUiState
    data class Error(val message: String) : RegisterUiState
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState: StateFlow<RegisterUiState> = _uiState

    fun registerWithEmailPassword(username: String, email: String, password: String) {
        _uiState.value = RegisterUiState.Loading
        viewModelScope.launch {
            val result = authRepository.registerWithEmailPassword(username, email, password)
            result.onSuccess { session ->
                _uiState.value = RegisterUiState.Success(session)
            }.onFailure { exception ->
                _uiState.value = RegisterUiState.Error(exception.message ?: "Registration failed")
            }
        }
    }
}