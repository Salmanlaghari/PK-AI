package com.salmanlaghari.pkai.ui.home

import androidx.lifecycle.ViewModel
import com.salmanlaghari.pkai.data.repository.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appRepository: AppRepository
) : ViewModel() {
    // Skeletons for MVVM and Clean Architecture can be extended here
}
