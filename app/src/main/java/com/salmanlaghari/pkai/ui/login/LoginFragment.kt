package com.salmanlaghari.pkai.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()

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

        // Developer Shortcut: Long-click Google Sign-In button to simulate Google Auth instantly!
        binding.btnGoogleSignin.setOnLongClickListener {
            android.util.Log.d("PKAI_AUTH", "Developer shortcut triggered: instant Google Sign-In simulation")
            viewModel.loginWithGoogle(
                idToken = "simulated_google_token_prince",
                displayName = "Prince Laghari",
                email = "admin.prince@salmanlaghari.com",
                photoUrl = "https://lh3.googleusercontent.com/a/default-user=s96-c"
            )
            true
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
                } else {
                    binding.tvErrorBanner.visibility = View.VISIBLE
                    binding.tvErrorBanner.text = getString(R.string.error_auth_failed)
                }
            } catch (e: androidx.credentials.exceptions.GetCredentialException) {
                android.util.Log.e("PKAI_AUTH", "GetCredentialException occurred: ", e)
                binding.tvErrorBanner.visibility = View.VISIBLE
                val userFriendlyMessage = when (e) {
                    is androidx.credentials.exceptions.GetCredentialCancellationException -> {
                        "Sign-In cancelled by user."
                    }
                    is androidx.credentials.exceptions.NoCredentialException -> {
                        showDiagnosticDialog()
                        "Google Sign-In configuration error on this device."
                    }
                    else -> {
                        "Google Sign-In error: ${e.message}\n\nPlease check your Firebase client configuration, package name, and SHA-1 fingerprints."
                    }
                }
                binding.tvErrorBanner.text = userFriendlyMessage
            } catch (e: Exception) {
                android.util.Log.e("PKAI_AUTH", "Unknown Google Sign-In exception occurred: ", e)
                binding.tvErrorBanner.visibility = View.VISIBLE
                binding.tvErrorBanner.text = "Google Sign-In failed: ${e.localizedMessage ?: "Unknown Error"}.\n\nPlease ensure you have replaced 'default_web_client_id' in strings.xml and configured Firebase."
            }
        }
    }

    private fun showDiagnosticDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_google_signin_diagnostic, null)
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Theme_PkAi)
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btn_diagnostic_dismiss)?.setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btn_simulated_login)?.setOnClickListener {
            dialog.dismiss()
            viewModel.loginWithGoogle(
                idToken = "simulated_google_token_prince",
                displayName = "Prince Laghari",
                email = "admin.prince@salmanlaghari.com",
                photoUrl = "https://lh3.googleusercontent.com/a/default-user=s96-c"
            )
        }

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
