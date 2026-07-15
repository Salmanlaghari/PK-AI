package com.salmanlaghari.pkai.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.databinding.FragmentProfileBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Observe user session status to dynamically show account data
        viewModel.userSession.observe(viewLifecycleOwner) { session ->
            if (session != null && session.isLoggedIn) {
                binding.tvProfileName.text = if (!session.displayName.isNullOrBlank()) {
                    session.displayName
                } else {
                    "Authenticated User"
                }

                binding.tvProfileEmail.text = if (!session.email.isNullOrBlank()) {
                    session.email
                } else {
                    "guest@pkai.com"
                }

                if (session.isGuest) {
                    binding.tvBadgeValue.text = getString(R.string.lbl_login_guest)
                    binding.tvBadgeValue.setTextColor(resources.getColor(R.color.electric_blue_glow, null))
                } else {
                    binding.tvBadgeValue.text = getString(R.string.lbl_login_google)
                    binding.tvBadgeValue.setTextColor(resources.getColor(R.color.purple_accent, null))
                }
            }
        }

        // Navigate to settings screen
        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_settingsFragment)
        }

        // Handle sign out
        binding.btnLogout.setOnClickListener {
            viewModel.logout {
                findNavController().navigate(R.id.action_profileFragment_to_loginFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
