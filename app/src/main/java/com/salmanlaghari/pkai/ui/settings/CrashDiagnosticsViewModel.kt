package com.salmanlaghari.pkai.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salmanlaghari.pkai.util.CrashDiagnosticsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CrashDiagnosticsViewModel @Inject constructor(
    private val crashDiagnosticsManager: CrashDiagnosticsManager
) : ViewModel() {

    val crashLog = crashDiagnosticsManager.crashLog.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val crashTimestamp = crashDiagnosticsManager.crashTimestamp.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun clearCrashLog() {
        viewModelScope.launch {
            crashDiagnosticsManager.clearCrashLog()
        }
    }
}
