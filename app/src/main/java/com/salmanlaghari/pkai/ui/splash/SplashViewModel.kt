package com.salmanlaghari.pkai.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val authRepository: AuthRepository
) : ViewModel() {
    val isOnboardingCompletedFlow = preferencesManager.isOnboardingCompleted
    val userSessionFlow = authRepository.getSession()

    fun autoLoginAsGuest() {
        viewModelScope.launch {
            // Mark onboarding as completed
            preferencesManager.setOnboardingCompleted(true)
            // Login as guest if not already logged in
            val current = authRepository.getSession().first()
            if (!current.isLoggedIn) {
                authRepository.loginAsGuest()
            }
        }
    }
}
