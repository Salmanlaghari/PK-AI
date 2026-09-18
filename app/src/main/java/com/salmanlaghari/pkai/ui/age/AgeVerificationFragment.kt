package com.salmanlaghari.pkai.ui.age

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.databinding.FragmentAgeVerificationBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AgeVerificationFragment : Fragment() {

    private var _binding: FragmentAgeVerificationBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAgeVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnConfirm18.setOnClickListener {
            lifecycleScope.launch {
                preferencesManager.setAgeVerified(true)
                val session = preferencesManager.userSession.first()
                if (session.isLoggedIn) {
                    findNavController().navigate(R.id.action_ageVerificationFragment_to_homeFragment)
                } else {
                    findNavController().navigate(R.id.action_ageVerificationFragment_to_loginFragment)
                }
            }
        }

        binding.btnDecline18.setOnClickListener {
            showAccessRestrictedDialog()
        }
    }

    private fun showAccessRestrictedDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Access Restricted")
            .setMessage("You must be 18 years or older to access PK AI. Access to chat and companion features is restricted.")
            .setCancelable(false)
            .setPositiveButton("Exit") { _, _ ->
                requireActivity().finish()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
