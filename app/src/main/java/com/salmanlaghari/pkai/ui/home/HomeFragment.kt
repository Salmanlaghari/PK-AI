package com.salmanlaghari.pkai.ui.home

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

    // Launcher for VoiceAssistantActivity to receive transcripts
    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val transcript = result.data?.getStringExtra("confirmed_transcript")
            if (!transcript.isNullOrBlank()) {
                binding.etMessageInput.setText(transcript)
                // Auto-send the message for a seamless hands-free experience
                viewModel.sendMessage(transcript)
                binding.etMessageInput.text?.clear()
            }
        }
    }

    private var rippleAnimator: ValueAnimator? = null

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

        // 8. Voice Trigger click opens premium VoiceAssistantActivity
        binding.btnVoice.setOnClickListener {
            val intent = Intent(requireContext(), com.salmanlaghari.pkai.ui.voice.VoiceAssistantActivity::class.java).apply {
                putExtra("selected_model", viewModel.selectedModel.value.name)
            }
            voiceLauncher.launch(intent)
        }

        // 9. Start continuous 60fps ripple ring animations for Voice Trigger
        startVoiceRippleAnimations()
    }

    private fun startVoiceRippleAnimations() {
        rippleAnimator?.cancel()
        rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val progress = anim.animatedValue as Float

                // Ripple 1
                if (_binding != null) {
                    binding.viewRipple1.scaleX = 0.8f + progress * 0.6f
                    binding.viewRipple1.scaleY = 0.8f + progress * 0.6f
                    binding.viewRipple1.alpha = (1f - progress) * 0.8f

                    // Ripple 2 (staggered delay, starts at half progress)
                    val progress2 = (progress + 0.5f) % 1f
                    binding.viewRipple2.scaleX = 0.8f + progress2 * 1.0f
                    binding.viewRipple2.scaleY = 0.8f + progress2 * 1.0f
                    binding.viewRipple2.alpha = (1f - progress2) * 0.5f
                }
            }
        }
        rippleAnimator?.start()
    }

    override fun onDestroyView() {
        rippleAnimator?.cancel()
        rippleAnimator = null
        super.onDestroyView()
        _binding = null
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

            // Grok 2, Perplexity Sonar, and ChatGPT are always Coming Soon and disabled
            val isModelDisabled = model == AiModel.GROK || model == AiModel.PERPLEXITY || model == AiModel.CHATGPT
            if (isModelDisabled) {
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

            // Highlight current selected model and apply custom selected backgrounds
            if (model == currentSelected) {
                itemBinding.ivModelCheck.visibility = View.VISIBLE
                itemBinding.tvModelName.setTextColor(resources.getColor(R.color.html_cyan, null))
                itemBinding.btnModelItem.setBackgroundResource(R.drawable.bg_model_item_selected)
            } else {
                itemBinding.ivModelCheck.visibility = View.GONE
                itemBinding.tvModelName.setTextColor(resources.getColor(R.color.white, null))
                itemBinding.btnModelItem.setBackgroundResource(R.drawable.bg_model_item_default)
            }

            if (!isModelDisabled) {
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
}
