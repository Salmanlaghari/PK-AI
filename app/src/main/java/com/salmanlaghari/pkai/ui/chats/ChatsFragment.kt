package com.salmanlaghari.pkai.ui.chats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.model.AiModel
import com.salmanlaghari.pkai.databinding.FragmentChatsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ChatsFragment : Fragment() {

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize active chats adapter with click callback
        val adapter = ActiveChatsAdapter { model ->
            val bundle = Bundle().apply {
                putString("selectedModelName", model.name)
            }
            findNavController().navigate(R.id.action_chatsFragment_to_homeFragment, bundle)
        }

        binding.rvActiveChats.adapter = adapter

        // Submit the 9 premium AI models
        adapter.submitList(AiModel.values().toList())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
