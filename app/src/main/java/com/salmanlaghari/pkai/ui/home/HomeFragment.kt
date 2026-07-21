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
import com.salmanlaghari.pkai.databinding.ItemToolCardBinding
import com.salmanlaghari.pkai.databinding.LayoutModelBottomSheetBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private data class AiTool(
    val name: String,
    val icon: String,
    val model: AiModel?,
    val isWorking: Boolean
)

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

        // 0. Automatically auto-select first working model at launch to prevent 404 errors
        val hasOpenRouter = com.salmanlaghari.pkai.BuildConfig.OPENROUTER_API_KEY.isNotBlank()
        val hasGemini = com.salmanlaghari.pkai.BuildConfig.GEMINI_API_KEY.isNotBlank()
        val hasOpenAi = com.salmanlaghari.pkai.BuildConfig.OPENAI_API_KEY.isNotBlank()

        if (hasOpenRouter && viewModel.selectedModel.value != AiModel.CLAUDE && viewModel.selectedModel.value != AiModel.DEEPSEEK) {
            viewModel.selectModel(AiModel.CLAUDE)
        } else if (hasGemini && viewModel.selectedModel.value != AiModel.GEMINI) {
            viewModel.selectModel(AiModel.GEMINI)
        } else if (hasOpenAi && viewModel.selectedModel.value != AiModel.CHATGPT) {
            viewModel.selectModel(AiModel.CHATGPT)
        }

        // 1. Setup Chat Adapter
        val chatAdapter = ChatAdapter()
        binding.rvChatMessages.adapter = chatAdapter

        // 2. Setup Model Selector Click (Shows Bottom Sheet)
        binding.btnModelSelector.setOnClickListener {
            showModelSelectionBottomSheet()
        }

        // 3. Observe Selected Model StateFlow to update UI & Grid highlighted state
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
                binding.btnModelSelector.text = "◆ $emoji ${model.displayName} ▾"

                // Redraw grids to highlight current active model selection
                populateToolGrids(model)
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
    }

    private fun populateToolGrids(activeModel: AiModel) {
        binding.gridActiveTools.removeAllViews()
        binding.gridBakiTools.removeAllViews()

        val hasOpenRouter = com.salmanlaghari.pkai.BuildConfig.OPENROUTER_API_KEY.isNotBlank()
        val hasGemini = com.salmanlaghari.pkai.BuildConfig.GEMINI_API_KEY.isNotBlank()
        val hasOpenAi = com.salmanlaghari.pkai.BuildConfig.OPENAI_API_KEY.isNotBlank()

        val isAllEmpty = !hasOpenRouter && !hasGemini && !hasOpenAi

        val tools = listOf(
            AiTool("Claude", "🧠", AiModel.CLAUDE, hasOpenRouter || isAllEmpty),
            AiTool("Gemini", "✨", AiModel.GEMINI, hasGemini || (isAllEmpty && !hasOpenRouter)),
            AiTool("GPT", "💬", AiModel.CHATGPT, hasOpenAi),
            AiTool("Image Gen", "🖼️", null, false),
            AiTool("Video Gen", "🎬", null, false),
            AiTool("Music Gen", "🎵", null, false)
        )

        tools.forEach { tool ->
            val cardBinding = ItemToolCardBinding.inflate(layoutInflater, null, false)
            cardBinding.tvToolIcon.text = tool.icon
            cardBinding.tvToolName.text = tool.name

            if (tool.isWorking) {
                // Active Card State
                cardBinding.cardRoot.setBackgroundResource(R.drawable.bg_glass_card_active)
                cardBinding.viewShine.visibility = View.VISIBLE
                cardBinding.tvToolStatus.text = "ACTIVE"
                cardBinding.tvToolStatus.setBackgroundResource(R.drawable.bg_status_active_badge)
                cardBinding.tvToolStatus.setTextColor(resources.getColor(R.color.premium_black, null))
                cardBinding.cardRoot.alpha = 1.0f

                // Under 3D Tilt floating look
                cardBinding.cardRoot.translationZ = 12f
                cardBinding.cardRoot.scaleX = 1.02f
                cardBinding.cardRoot.scaleY = 1.02f

                // Click selector highlight if selected
                if (tool.model != null && tool.model == activeModel) {
                    cardBinding.tvToolName.setTextColor(resources.getColor(R.color.cyan, null))
                } else {
                    cardBinding.tvToolName.setTextColor(resources.getColor(R.color.white, null))
                }

                // Click selection callback
                if (tool.model != null) {
                    cardBinding.cardRoot.setOnClickListener {
                        viewModel.selectModel(tool.model)
                    }
                }

                binding.gridActiveTools.addView(cardBinding.root)
            } else {
                // Non-working / Locked State (Coming Soon)
                cardBinding.cardRoot.setBackgroundResource(R.drawable.bg_glass_card)
                cardBinding.viewShine.visibility = View.GONE
                cardBinding.tvToolStatus.text = "Coming Soon"
                cardBinding.tvToolStatus.setBackgroundResource(R.drawable.bg_frosted_glass_pill)
                cardBinding.tvToolStatus.setTextColor(resources.getColor(R.color.text_dim, null))
                cardBinding.cardRoot.alpha = 0.62f
                cardBinding.cardRoot.isClickable = false
                cardBinding.cardRoot.isFocusable = false

                binding.gridBakiTools.addView(cardBinding.root)
            }
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

            // ChatGPT/OpenAI is disabled if its key is empty
            val isOpenAiDisabled = model == AiModel.CHATGPT && com.salmanlaghari.pkai.BuildConfig.OPENAI_API_KEY.isBlank()
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
