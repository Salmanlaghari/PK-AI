package com.salmanlaghari.pkai.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.model.ChatHistoryItem
import com.salmanlaghari.pkai.databinding.FragmentHistoryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Initialize History Adapter with callbacks for all premium actions
        val adapter = ChatHistoryAdapter(
            onRename = { chat -> showRenameDialog(chat) },
            onPin = { chat -> viewModel.togglePinItem(chat) },
            onDelete = { chat -> viewModel.deleteItem(chat.id) },
            onShare = { chat -> shareHistoryItem(chat) },
            onClick = { chat ->
                Toast.makeText(requireContext(), "Opening conversation: ${chat.title}", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvHistory.adapter = adapter

        // 2. Bind search edit text changes
        binding.etSearchHistory.doAfterTextChanged { text ->
            viewModel.updateSearchQuery(text?.toString().orEmpty())
        }

        // 3. Observe Grouped Conversations from StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.groupedHistory.collect { list ->
                adapter.submitList(list)
            }
        }
    }

    private fun showRenameDialog(chat: ChatHistoryItem) {
        val input = EditText(requireContext())
        input.setText(chat.title)
        input.setSelection(chat.title.length)

        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_PkAi)
            .setTitle("✏️ Rename Conversation")
            .setView(input)
            .setPositiveButton("Rename") { dialog, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotBlank()) {
                    viewModel.renameItem(chat.id, newTitle)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun shareHistoryItem(chat: ChatHistoryItem) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Share Chat Session")
            putExtra(Intent.EXTRA_TEXT, "Shared from PK AI Super App:\n\nSession: ${chat.title}\nLast Message: ${chat.lastMessage}")
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
