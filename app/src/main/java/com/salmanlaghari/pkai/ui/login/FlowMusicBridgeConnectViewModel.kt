package com.salmanlaghari.pkai.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salmanlaghari.pkai.ui.aihub.FlowMusicSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the Flow Music auto-connect exchange across configuration changes.
 *
 * The ID-token -> Supabase session exchange runs in [viewModelScope], which
 * survives rotation: the recreated LoginFragment re-observes [state] and
 * re-shows the "connecting" popup instead of the rotation silently aborting
 * the work the popup announced. The scope is fragment-scoped, so navigating
 * away permanently still cancels the exchange (no leak).
 */
@HiltViewModel
class FlowMusicBridgeConnectViewModel @Inject constructor(
    private val sessionManager: FlowMusicSessionManager
) : ViewModel() {

    sealed interface ConnectState {
        data object Idle : ConnectState
        data class Connecting(val email: String?) : ConnectState
        data object Connected : ConnectState
        data object Failed : ConnectState
    }

    private val _state = MutableStateFlow<ConnectState>(ConnectState.Idle)
    val state: StateFlow<ConnectState> = _state.asStateFlow()

    /**
     * Starts the exchange unless one already ran or is running
     * (double-tap / re-entry safety).
     */
    fun connect(idToken: String, email: String?) {
        val current = _state.value
        if (current is ConnectState.Connecting || current is ConnectState.Connected) {
            Log.d("PKAI_AUTH", "Bridge connect already in progress or done; ignoring re-entry")
            return
        }
        _state.value = ConnectState.Connecting(email)
        viewModelScope.launch {
            val ok = try {
                sessionManager.connectWithIdToken(idToken)
            } catch (e: Exception) {
                Log.e("PKAI_AUTH", "Flow Music auto-connect failed", e)
                false
            }
            _state.value = if (ok) ConnectState.Connected else ConnectState.Failed
        }
    }
}
