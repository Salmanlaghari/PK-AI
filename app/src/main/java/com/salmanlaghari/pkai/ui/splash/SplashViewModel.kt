package com.salmanlaghari.pkai.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    preferencesManager: PreferencesManager
) : ViewModel() {
    val isOnboardingCompleted = preferencesManager.isOnboardingCompleted.asLiveData()
}
