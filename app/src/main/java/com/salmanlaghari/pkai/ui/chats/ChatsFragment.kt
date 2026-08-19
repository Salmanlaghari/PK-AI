package com.salmanlaghari.pkai.ui.chats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.model.LlmProvider
import com.salmanlaghari.pkai.databinding.FragmentChatsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class ChatsFragment : Fragment() {

    @Inject
    lateinit var prefs: com.salmanlaghari.pkai.data.local.datastore.PreferencesManager

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
        val adapter = ActiveChatsAdapter { provider ->
            // Persist the chosen provider (same selection as Settings → AI)
            runBlocking { prefs.setSelectedProviderId(provider.id) }
            val bundle = Bundle().apply {
                putString("selectedModelName", provider.id)
            }
            findNavController().navigate(R.id.action_chatsFragment_to_homeFragment, bundle)
        }

        binding.rvActiveChats.adapter = adapter

        // Submit the free LLM provider catalogue
        adapter.submitList(LlmProvider.ALL)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
