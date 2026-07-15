package com.salmanlaghari.pkai.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
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
                binding.btnModelSelector.text = "💎 ${model.displayName} ▼"
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

        // 7. Header Actions (Clear Conversation & Sign Out)
        binding.btnClearChat.setOnClickListener {
            viewModel.clearConversation()
        }

        binding.btnLogout.setOnClickListener {
            viewModel.logout {
                findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
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
            itemBinding.tvModelProvider.text = model.providerName

            // Highlight current selected model
            if (model == currentSelected) {
                itemBinding.ivModelCheck.visibility = View.VISIBLE
                itemBinding.tvModelName.setTextColor(resources.getColor(R.color.electric_blue_glow, null))
            } else {
                itemBinding.ivModelCheck.visibility = View.GONE
                itemBinding.tvModelName.setTextColor(resources.getColor(R.color.white, null))
            }

            itemBinding.btnModelItem.setOnClickListener {
                viewModel.selectModel(model)
                dialog.dismiss()
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
