package com.salmanlaghari.pkai.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Back arrow listener
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // Observe Dark Mode
        viewModel.isDarkMode.observe(viewLifecycleOwner) { isDark ->
            binding.switchDarkMode.isChecked = isDark
            val nightMode = if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
                AppCompatDelegate.setDefaultNightMode(nightMode)
            }
        }

        // Observe Language selection
        viewModel.appLanguage.observe(viewLifecycleOwner) { langCode ->
            val languageName = when (langCode) {
                "ur" -> "Urdu"
                "sd" -> "Sindhi"
                else -> "English"
            }
            binding.tvCurrentLanguage.text = languageName
        }

        // Observe Notifications
        viewModel.isNotificationsEnabled.observe(viewLifecycleOwner) { enabled ->
            binding.switchNotifications.isChecked = enabled
        }

        // Handle Switch listeners
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setDarkMode(isChecked)
        }

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setNotificationsEnabled(isChecked)
            val msg = if (isChecked) "Notifications enabled" else "Notifications disabled"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // Handle Language selection dialog
        binding.rowLanguage.setOnClickListener {
            showLanguageDialog()
        }

        // Handle Privacy Policy dialog
        binding.rowPrivacy.setOnClickListener {
            showPrivacyDialog()
        }

        // Handle Terms & Conditions dialog
        binding.rowTerms.setOnClickListener {
            showTermsDialog()
        }

        // Navigate to About Screen
        binding.rowAbout.setOnClickListener {
            findNavController().navigate(R.id.action_settingsFragment_to_aboutFragment)
        }
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Urdu", "Sindhi")
        val langCodes = arrayOf("en", "ur", "sd")

        val currentLangCode = viewModel.appLanguage.value ?: "en"
        val selectedIndex = langCodes.indexOf(currentLangCode).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_PkAi)
            .setTitle("Select App Language")
            .setSingleChoiceItems(languages, selectedIndex) { dialog, which ->
                val selectedLangCode = langCodes[which]
                viewModel.setAppLanguage(selectedLangCode)

                // Set app-wide dynamic locales
                val appLocales = LocaleListCompat.forLanguageTags(selectedLangCode)
                AppCompatDelegate.setApplicationLocales(appLocales)

                Toast.makeText(requireContext(), "Language changed to ${languages[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPrivacyDialog() {
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_PkAi)
            .setTitle("Privacy Policy")
            .setMessage("Your privacy is extremely important to us. PK AI does not sell, trade, or share your account credentials, Google sign-in identities, or device preference cache with third-party networks. All search outputs, log metrics, and preference properties are stored locally inside safe sandbox environments or processed over secure SSL API servers.")
            .setPositiveButton("I Understand", null)
            .show()
    }

    private fun showTermsDialog() {
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_PkAi)
            .setTitle("Terms of Service")
            .setMessage("By accessing the PK AI Super App platform, you agree to comply with safe usage guidelines. Automated querying, malicious scripting, payload injections, or hacking API endpoints is strictly prohibited. PK AI reserves all rights to suspend guest or premium sessions that violate standard device utilization practices.")
            .setPositiveButton("Accept", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
