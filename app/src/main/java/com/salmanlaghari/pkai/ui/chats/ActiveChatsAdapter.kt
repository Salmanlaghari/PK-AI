package com.salmanlaghari.pkai.ui.chats

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salmanlaghari.pkai.data.model.LlmProvider
import com.salmanlaghari.pkai.databinding.ItemActiveChatBinding

/**
 * Browses the catalogue of available free LLM providers (the same list shown in
 * Settings → AI). Tapping a row opens a chat and persists the selection.
 */
class ActiveChatsAdapter(
    private val onClick: (LlmProvider) -> Unit
) : ListAdapter<LlmProvider, ActiveChatsAdapter.ViewHolder>(LlmProviderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemActiveChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemActiveChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(provider: LlmProvider) {
            binding.tvAvatar.text = provider.logoEmoji
            binding.tvName.text = provider.displayName
            binding.tvProvider.text = provider.tagline

            // Status Badge is set statically to "● Online"
            binding.tvStatusBadge.text = "● Online"

            binding.root.setOnClickListener { onClick(provider) }
            binding.btnOpenChat.setOnClickListener { onClick(provider) }
        }
    }

    private class LlmProviderDiffCallback : DiffUtil.ItemCallback<LlmProvider>() {
        override fun areItemsTheSame(oldItem: LlmProvider, newItem: LlmProvider): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LlmProvider, newItem: LlmProvider): Boolean {
            return oldItem == newItem
        }
    }
}
