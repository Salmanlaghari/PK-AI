package com.salmanlaghari.pkai.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.salmanlaghari.pkai.MainActivity
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private var isGuest = false
    private var guestMessageCount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 0. Setup Chat Adapter
        val chatAdapter = ChatAdapter()
        binding.rvChatMessages.adapter = chatAdapter

        // 1. Observe guest session + message count for the 10-message hard limit
        lifecycleScope.launch {
            preferencesManager.userSession.collect { session ->
                isGuest = session.isLoggedIn && session.isGuest
            }
        }
        lifecycleScope.launch {
            preferencesManager.guestMessageCount.collect { count ->
                guestMessageCount = count
            }
        }

        // 2. Setup PK AI mode label (no provider names are ever shown)
        binding.btnModelSelector.setOnClickListener {
            if (viewModel.isFreeMode.value) {
                Toast.makeText(
                    requireContext(),
                    "Web AI is a Premium feature — switch to Premium to enable.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                viewModel.setWebSearchMode(!viewModel.webSearchMode.value)
            }
        }

        // 3. Observe Free Mode StateFlow to Update UI
        lifecycleScope.launch {
            viewModel.isFreeMode.collect { isFree ->
                if (isFree) {
                    binding.btnTabPremium.setTextColor(resources.getColor(R.color.outline, null))
                    binding.btnTabPremium.setBackgroundColor(resources.getColor(android.R.color.transparent, null))
                    binding.btnTabFree.setTextColor(resources.getColor(R.color.electric_blue_glow, null))
                    binding.btnTabFree.setBackgroundColor(resources.getColor(R.color.glass_background, null))
                    binding.btnModelSelector.text = "🌍 Free Public AI"
                    binding.etMessageInput.setHint("Ask Free Public AI anything...")
                } else {
                    binding.btnTabPremium.setTextColor(resources.getColor(R.color.white, null))
                    binding.btnTabPremium.setBackgroundColor(resources.getColor(R.color.glass_background, null))
                    binding.btnTabFree.setTextColor(resources.getColor(R.color.outline, null))
                    binding.btnTabFree.setBackgroundColor(resources.getColor(android.R.color.transparent, null))
                    binding.etMessageInput.setHint("Ask PK AI anything...")
                }
            }
        }

        // 3b. Observe Web Search Mode to update the PK AI label
        lifecycleScope.launch {
            viewModel.webSearchMode.collect { web ->
                if (!viewModel.isFreeMode.value) {
                    binding.btnModelSelector.text = if (web) "🌐 Web AI" else "PK AI"
                }
            }
        }

        // 3c. Tab Mode Toggle Click Listeners
        binding.btnTabPremium.setOnClickListener { viewModel.setFreeMode(false) }
        binding.btnTabFree.setOnClickListener { viewModel.setFreeMode(true) }

        // 4. Observe Messages StateFlow
        lifecycleScope.launch {
            viewModel.chatMessages.collect { messages ->
                chatAdapter.submitList(messages) {
                    if (messages.isNotEmpty()) {
                        binding.rvChatMessages.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }

        // 5. Observe Typing StateFlow
        lifecycleScope.launch {
            viewModel.isGenerating.collect { isGenerating ->
                binding.layoutTyping.visibility = if (isGenerating) View.VISIBLE else View.GONE
                binding.btnSend.isEnabled = !isGenerating
                binding.btnTools.isEnabled = !isGenerating
            }
        }

        // 6. Tools / Attachment button opens the tools bottom sheet
        binding.btnTools.setOnClickListener { openToolsBottomSheet() }

        // 7. Send Button Click Action (with guest limit enforcement)
        binding.btnSend.setOnClickListener {
            val content = binding.etMessageInput.text?.toString().orEmpty()
            if (content.isNotBlank()) {
                if (isGuest && guestMessageCount >= 10) {
                    showGuestLimitDialog()
                    return@setOnClickListener
                }
                viewModel.sendMessage(content)
                if (isGuest) {
                    lifecycleScope.launch { preferencesManager.incrementGuestMessageCount() }
                }
                binding.etMessageInput.text?.clear()
            }
        }

        // 8. Premium Header Toolbar Actions
        binding.btnMenu.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        binding.btnNotifications.setOnClickListener {
            Toast.makeText(requireContext(), "🔔 Notifications clicked!", Toast.LENGTH_SHORT).show()
        }

        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }
    }

    private fun openToolsBottomSheet() {
        val context = requireContext()
        val dialog = BottomSheetDialog(context)
        val scroll = ScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
        }

        val tools = listOf(
            "🌐 Web Search (AI Mode)" to true,
            "💬 Chat" to false,
            "🖼 Image" to false,
            "🎥 Video" to false,
            "🎵 Music" to false,
            "📄 PDF" to false,
            "💻 Code" to false
        )

        tools.forEach { (label, isWeb) ->
            val btn = MaterialButton(context).apply {
                text = label
                setTextColor(resources.getColor(R.color.white, null))
                setBackgroundColor(resources.getColor(R.color.glass_surface, null))
                cornerRadius = 24
                setPadding(24, 20, 24, 20)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 12 }
                layoutParams = lp
            }
            btn.setOnClickListener {
                if (isWeb) {
                    viewModel.setWebSearchMode(true)
                    Toast.makeText(context, "🌐 Web AI Mode enabled", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "$label is part of PK AI Premium", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            layout.addView(btn)
        }

        scroll.addView(layout)
        dialog.setContentView(scroll)
        dialog.show()
    }

    private fun showGuestLimitDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Guest limit reached")
            .setMessage("You've used all 10 free messages. Sign in with Google to continue using PK AI.")
            .setPositiveButton("Sign in with Google") { _, _ ->
                findNavController().navigate(R.id.loginFragment)
            }
            .setNegativeButton("Maybe later", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
