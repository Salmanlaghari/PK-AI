package com.salmanlaghari.pkai.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salmanlaghari.pkai.ui.aihub.FlowMusicSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the Flow Music auto-connect exchange across configuration changes AND
 * the Login -> Home navigation.
 *
 * The ID-token -> Supabase session exchange runs in [viewModelScope]. The
 * ViewModel is obtained with `activityViewModels()`, so the scope survives
 * both rotation AND the `popUpTo loginFragment (inclusive)` navigation that
 * destroys LoginFragment right after a successful PK-AI sign-in — a
 * fragment-scoped ViewModel would have its scope cancelled there, aborting
 * the in-flight exchange the "connecting" popup just announced. The scope is
 * activity-scoped, so the work still ends with the activity (no leak).
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

    /** ID token of the exchange currently running or completed. */
    private var lastToken: String? = null

    /**
     * Starts the exchange unless the same token is already running or done
     * (double-tap / re-entry safety). A NEW token (fresh login, e.g. after a
     * logout in the same activity lifetime) always starts a fresh exchange.
     */
    fun connect(idToken: String, email: String?) {
        val current = _state.value
        if (current is ConnectState.Connecting) {
            Log.d("PKAI_AUTH", "Bridge connect already in progress; ignoring re-entry")
            return
        }
        if (current is ConnectState.Connected && idToken == lastToken) {
            Log.d("PKAI_AUTH", "Bridge already connected for this token; ignoring re-entry")
            return
        }
        lastToken = idToken
        _state.value = ConnectState.Connecting(email)
        viewModelScope.launch {
            val ok = try {
                sessionManager.connectWithIdToken(idToken)
            } catch (e: CancellationException) {
                // Scope cancelled (activity going away): let structured
                // concurrency unwind instead of reporting a fake failure.
                throw e
            } catch (e: Exception) {
                Log.e("PKAI_AUTH", "Flow Music auto-connect failed", e)
                false
            }
            _state.value = if (ok) ConnectState.Connected else ConnectState.Failed
        }
    }
}
