package com.salmanlaghari.pkai.ui.login

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.databinding.FragmentRegisterBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegisterViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up the login prompt with styled text
        binding.tvLoginPrompt.text = Html.fromHtml(getString(R.string.prompt_login), Html.FROM_HTML_MODE_LEGACY)

        // Observe UI state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is RegisterUiState.Idle -> {
                        binding.layoutLoading.visibility = View.GONE
                        binding.layoutAuthOptions.visibility = View.VISIBLE
                        binding.tvErrorBanner.visibility = View.GONE
                    }
                    is RegisterUiState.Loading -> {
                        binding.layoutLoading.visibility = View.VISIBLE
                        binding.layoutAuthOptions.visibility = View.GONE
                        binding.tvErrorBanner.visibility = View.GONE
                    }
                    is RegisterUiState.Success -> {
                        binding.layoutLoading.visibility = View.GONE
                        findNavController().navigate(R.id.action_registerFragment_to_homeFragment)
                    }
                    is RegisterUiState.Error -> {
                        binding.layoutLoading.visibility = View.GONE
                        binding.layoutAuthOptions.visibility = View.VISIBLE
                        binding.tvErrorBanner.visibility = View.VISIBLE
                        binding.tvErrorBanner.text = state.message
                    }
                }
            }
        }

        binding.tvLoginPrompt.setOnClickListener {
            findNavController().navigate(R.id.action_registerFragment_to_loginFragment)
        }

        binding.btnRegister.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                binding.tvErrorBanner.visibility = View.VISIBLE
                binding.tvErrorBanner.text = "Please fill in all fields."
                return@setOnClickListener
            }

            viewModel.registerWithEmailPassword(username, email, password)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}