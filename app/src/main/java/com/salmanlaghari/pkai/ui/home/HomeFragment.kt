package com.salmanlaghari.pkai.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.salmanlaghari.pkai.MainActivity
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.model.AiModel
import com.salmanlaghari.pkai.databinding.FragmentHomeBinding
import com.salmanlaghari.pkai.databinding.ItemModelSheetBinding
import com.salmanlaghari.pkai.databinding.LayoutModelBottomSheetBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 0. Parse potential model argument passed from other destinations (e.g. ChatsFragment)
        arguments?.getString("selectedModelName")?.let { modelName ->
            try {
                val model = AiModel.valueOf(modelName)
                viewModel.selectModel(model)
                // Clear the argument so it doesn't persist on configuration changes / subsequent navigations
                arguments?.remove("selectedModelName")
            } catch (e: Exception) {
                // Ignore invalid model name
            }
        }

        // 1. Setup Chat Adapter
        val chatAdapter = ChatAdapter()
        binding.rvChatMessages.adapter = chatAdapter

        // 2. Setup Model Selector Click (Shows Bottom Sheet)
        binding.btnModelSelector.setOnClickListener {
            showModelSelectionBottomSheet()
        }

        // 3. Observe Selected Model StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedModel.collect { model ->
                val emoji = when (model) {
                    AiModel.GEMINI -> "💎"
                    AiModel.CHATGPT -> "🤖"
                    AiModel.CLAUDE -> "🧠"
                    AiModel.GROK -> "⚡"
                    AiModel.DEEPSEEK -> "🌊"
                    AiModel.QWEN -> "🐪"
                    AiModel.LLAMA -> "🦙"
                    AiModel.MISTRAL -> "🌪️"
                    AiModel.PERPLEXITY -> "🔍"
                }
                binding.btnModelSelector.text = "$emoji ${model.displayName} ▼"
            }
        }

        // 4. Observe Messages StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.chatMessages.collect { messages ->
                chatAdapter.submitList(messages) {
                    if (messages.isNotEmpty()) {
                        binding.rvChatMessages.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }

        // 5. Observe Typing StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isGenerating.collect { isGenerating ->
                binding.layoutTyping.visibility = if (isGenerating) View.VISIBLE else View.GONE
                binding.btnSend.isEnabled = !isGenerating
            }
        }

        // 6. Send Button Click Action
        binding.btnSend.setOnClickListener {
            val content = binding.etMessageInput.text?.toString().orEmpty()
            if (content.isNotBlank()) {
                viewModel.sendMessage(content)
                binding.etMessageInput.text?.clear()
            }
        }

        // 7. Premium Header Toolbar Actions
        binding.btnMenu.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        binding.btnNotifications.setOnClickListener {
            Toast.makeText(requireContext(), "🔔 Notifications clicked!", Toast.LENGTH_SHORT).show()
        }

        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        // 8. Quick Actions Click Listeners (Placeholders only)
        setupQuickActions()
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupPremiumTouchAnimation(card: com.google.android.material.card.MaterialCardView) {
        val originalElevation = card.cardElevation
        card.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    view.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(120)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                    card.cardElevation = originalElevation * 0.3f
                }
                android.view.MotionEvent.ACTION_UP -> {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(220)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2.0f))
                        .start()
                    card.cardElevation = originalElevation

                    val rect = android.graphics.Rect()
                    view.getGlobalVisibleRect(rect)
                    if (rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                        view.performClick()
                    }
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                    card.cardElevation = originalElevation
                }
            }
            true
        }
    }

    private fun setupQuickActions() {
        setupPremiumTouchAnimation(binding.cardQaChat)
        setupPremiumTouchAnimation(binding.cardQaImage)
        setupPremiumTouchAnimation(binding.cardQaVideo)
        setupPremiumTouchAnimation(binding.cardQaMusic)
        setupPremiumTouchAnimation(binding.cardQaPdf)
        setupPremiumTouchAnimation(binding.cardQaCode)
        setupPremiumTouchAnimation(binding.cardQaSearch)

        binding.cardQaChat.setOnClickListener {
            Toast.makeText(requireContext(), "💬 Premium Chat Generator is active", Toast.LENGTH_SHORT).show()
        }
        binding.cardQaImage.setOnClickListener {
            Toast.makeText(requireContext(), "🖼 Premium Image Generator Placeholder", Toast.LENGTH_SHORT).show()
        }
        binding.cardQaVideo.setOnClickListener {
            Toast.makeText(requireContext(), "🎥 Premium Video Generator Placeholder", Toast.LENGTH_SHORT).show()
        }
        binding.cardQaMusic.setOnClickListener {
            Toast.makeText(requireContext(), "🎵 Premium Music Generator Placeholder", Toast.LENGTH_SHORT).show()
        }
        binding.cardQaPdf.setOnClickListener {
            Toast.makeText(requireContext(), "📄 Premium PDF AI Analyst Placeholder", Toast.LENGTH_SHORT).show()
        }
        binding.cardQaCode.setOnClickListener {
            Toast.makeText(requireContext(), "💻 Premium Code Assistant Placeholder", Toast.LENGTH_SHORT).show()
        }
        binding.cardQaSearch.setOnClickListener {
            Toast.makeText(requireContext(), "🌐 Premium Web Search Assistant Placeholder", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showModelSelectionBottomSheet() {
        val context = requireContext()
        val dialog = BottomSheetDialog(context)
        val sheetBinding = LayoutModelBottomSheetBinding.inflate(layoutInflater)

        val models = AiModel.values()
        val currentSelected = viewModel.selectedModel.value

        models.forEach { model ->
            val itemBinding = ItemModelSheetBinding.inflate(layoutInflater, sheetBinding.layoutModelsList, false)
            itemBinding.tvModelName.text = model.displayName

            // Emoji mapping
            itemBinding.tvModelEmoji.text = when (model) {
                AiModel.GEMINI -> "💎"
                AiModel.CHATGPT -> "🤖"
                AiModel.CLAUDE -> "🧠"
                AiModel.GROK -> "⚡"
                AiModel.DEEPSEEK -> "🌊"
                AiModel.QWEN -> "🐪"
                AiModel.LLAMA -> "🦙"
                AiModel.MISTRAL -> "🌪️"
                AiModel.PERPLEXITY -> "🔍"
            }

            // ChatGPT/OpenAI is always Coming Soon and disabled
            val isOpenAiDisabled = model == AiModel.CHATGPT
            if (isOpenAiDisabled) {
                itemBinding.tvModelProvider.text = "Coming Soon"
                itemBinding.tvModelProvider.setTextColor(resources.getColor(R.color.error, null))
                itemBinding.tvModelName.alpha = 0.5f
                itemBinding.tvModelEmoji.alpha = 0.5f
                itemBinding.btnModelItem.isEnabled = false
                itemBinding.btnModelItem.alpha = 0.6f
            } else {
                itemBinding.tvModelProvider.text = model.providerName
                itemBinding.tvModelProvider.setTextColor(resources.getColor(R.color.outline, null))
                itemBinding.tvModelName.alpha = 1.0f
                itemBinding.tvModelEmoji.alpha = 1.0f
                itemBinding.btnModelItem.isEnabled = true
                itemBinding.btnModelItem.alpha = 1.0f
            }

            // Highlight current selected model
            if (model == currentSelected) {
                itemBinding.ivModelCheck.visibility = View.VISIBLE
                itemBinding.tvModelName.setTextColor(resources.getColor(R.color.electric_blue_glow, null))
            } else {
                itemBinding.ivModelCheck.visibility = View.GONE
                itemBinding.tvModelName.setTextColor(resources.getColor(R.color.white, null))
            }

            if (!isOpenAiDisabled) {
                itemBinding.btnModelItem.setOnClickListener {
                    viewModel.selectModel(model)
                    dialog.dismiss()
                }
            }

            sheetBinding.layoutModelsList.addView(itemBinding.root)
        }

        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
