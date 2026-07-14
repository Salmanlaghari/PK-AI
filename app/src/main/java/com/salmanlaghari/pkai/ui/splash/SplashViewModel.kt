package com.salmanlaghari.pkai.ui.splash

import androidx.lifecycle.ViewModel
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    preferencesManager: PreferencesManager,
    authRepository: AuthRepository
) : ViewModel() {
    val isOnboardingCompletedFlow = preferencesManager.isOnboardingCompleted
    val userSessionFlow = authRepository.getSession()
}
