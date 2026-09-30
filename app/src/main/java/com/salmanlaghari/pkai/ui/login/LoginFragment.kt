package com.salmanlaghari.pkai.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.databinding.FragmentLoginBinding
import com.salmanlaghari.pkai.ui.aihub.FlowMusicSessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()

    @Inject
    lateinit var flowMusicSessionManager: FlowMusicSessionManager

    /**
     * The Flow Music auto-connect notification popup. Kept as a field so it
     * is always dismissed in [onDestroyView]: if the device rotates while
     * the background exchange is running, a dialog still attached to the
     * old window would leak it (WindowLeaked).
     */
    private var bridgeDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe UI state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is LoginUiState.Idle -> {
                        binding.layoutLoading.visibility = View.GONE
                        binding.layoutAuthOptions.visibility = View.VISIBLE
                        binding.tvErrorBanner.visibility = View.GONE
                    }
                    is LoginUiState.Loading -> {
                        binding.layoutLoading.visibility = View.VISIBLE
                        binding.layoutAuthOptions.visibility = View.GONE
                        binding.tvErrorBanner.visibility = View.GONE
                    }
                    is LoginUiState.Success -> {
                        binding.layoutLoading.visibility = View.GONE
                        // Navigate to Home Dashboard upon successful login
                        findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
                    }
                    is LoginUiState.Error -> {
                        binding.layoutLoading.visibility = View.GONE
                        binding.layoutAuthOptions.visibility = View.VISIBLE
                        binding.tvErrorBanner.visibility = View.VISIBLE
                        binding.tvErrorBanner.text = state.message
                    }
                }
            }
        }

        // Trigger Google Sign-In with official Android Credential Manager
        binding.btnGoogleSignin.setOnClickListener {
            triggerGoogleSignIn()
        }

        // Sign in as guest
        binding.btnGuestSignin.setOnClickListener {
            viewModel.loginAsGuest()
        }
    }

    private fun triggerGoogleSignIn() {
        val credentialManager = CredentialManager.create(requireContext())
        val clientId = getString(R.string.default_web_client_id)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.resetState()
                android.util.Log.d("PKAI_AUTH", "Requesting credentials with client ID: $clientId")
                val result = credentialManager.getCredential(
                    request = request,
                    context = requireContext()
                )
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    val displayName = googleIdTokenCredential.displayName
                    val email = googleIdTokenCredential.id
                    val photoUrl = googleIdTokenCredential.profilePictureUri?.toString()

                    android.util.Log.i("PKAI_AUTH", "Google Sign-In Success! Email: $email")
                    viewModel.loginWithGoogle(
                        idToken = idToken,
                        displayName = displayName,
                        email = email,
                        photoUrl = photoUrl
                    )
                    // Same Google account -> Flow Music bridge auto-connect
                    // (notification popup + background session exchange).
                    autoConnectFlowMusicBridge(idToken, email)
                } else {
                    binding.tvErrorBanner.visibility = View.VISIBLE
                    binding.tvErrorBanner.text = getString(R.string.error_auth_failed)
                }
            } catch (e: androidx.credentials.exceptions.GetCredentialException) {
                android.util.Log.e("PKAI_AUTH", "GetCredentialException occurred: ", e)
                binding.tvErrorBanner.visibility = View.VISIBLE
                val userFriendlyMessage = when (e) {
                    is androidx.credentials.exceptions.GetCredentialCancellationException -> {
                        "Sign-In cancelled. You can continue as a Guest instead."
                    }
                    is androidx.credentials.exceptions.NoCredentialException -> {
                        "No Google account found on this device. You can continue as a Guest instead."
                    }
                    else -> {
                        "Google Sign-In failed. Please try again or continue as a Guest."
                    }
                }
                binding.tvErrorBanner.text = userFriendlyMessage
            } catch (e: Exception) {
                android.util.Log.e("PKAI_AUTH", "Unknown Google Sign-In exception occurred: ", e)
                binding.tvErrorBanner.visibility = View.VISIBLE
                binding.tvErrorBanner.text = "Google Sign-In failed. Please try again or continue as a Guest."
            }
        }
    }

    override fun onDestroyView() {
        bridgeDialog?.let { dialog ->
            if (dialog.isShowing) {
                try {
                    dialog.dismiss()
                } catch (e: Exception) {
                    // Best effort; the window may already be gone.
                }
            }
        }
        bridgeDialog = null
        super.onDestroyView()
        _binding = null
    }

    /**
     * Step 2 of sign-up: the SAME Google account the user just signed into
     * PK-AI with is connected to the Flow Music bridge automatically.
     *
     * A small notification popup tells the user what is happening while the
     * ID-token -> Supabase session exchange runs in the background. When this
     * Google account already owns a Flow Music account, Supabase signs it
     * into that EXISTING account - the user just continues with their own
     * account, nothing manual needed.
     *
     * Uses the Activity lifecycle scope so the exchange survives the
     * navigation to Home that follows a successful sign-in.
     */
    private fun autoConnectFlowMusicBridge(idToken: String, email: String?) {
        val accountLabel = email?.takeIf { it.isNotBlank() } ?: getString(R.string.msg_music_engine_default_account)
        // Resolve all UI strings now: the fragment may be detached (navigated
        // to Home) by the time the background exchange finishes.
        val connectedMessage = getString(R.string.msg_music_connected)
        bridgeDialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.title_music_engine))
            .setMessage(getString(R.string.msg_music_engine_connecting, accountLabel))
            .setCancelable(false)
            .create()
            .also { it.show() }

        // Capture the app context now: the login fragment may be gone
        // (navigated to Home) by the time the exchange finishes.
        val appContext = requireContext().applicationContext
        requireActivity().lifecycleScope.launch {
            val connected = try {
                flowMusicSessionManager.connectWithIdToken(idToken)
            } catch (e: Exception) {
                android.util.Log.e("PKAI_AUTH", "Flow Music auto-connect failed", e)
                false
            }
            // Dismiss via the field; onDestroyView() already handles the
            // rotation case, this covers the normal completion path.
            val dialog = bridgeDialog
            bridgeDialog = null
            if (dialog?.isShowing == true) {
                try {
                    dialog.dismiss()
                } catch (e: Exception) {
                    android.util.Log.w("PKAI_AUTH", "Popup dismiss skipped: ${e.message}")
                }
            }
            if (connected) {
                Toast.makeText(appContext, connectedMessage, Toast.LENGTH_SHORT).show()
            } else {
                // Silent fallback: AI Hub retries automatically on next open.
                android.util.Log.w(
                    "PKAI_AUTH",
                    "Flow Music auto-connect deferred; AI Hub will retry silently"
                )
            }
        }
    }
}
