package com.salmanlaghari.pkai.ui.chats

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.model.AiModel
import com.salmanlaghari.pkai.databinding.ItemActiveChatBinding

class ActiveChatsAdapter(
    private val onClick: (AiModel) -> Unit
) : ListAdapter<AiModel, ActiveChatsAdapter.ViewHolder>(AiModelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemActiveChatBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemActiveChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(model: AiModel) {
            binding.tvAvatar.text = getEmoji(model)
            binding.tvName.text = model.displayName

            // ChatGPT/OpenAI is always Coming Soon and disabled
            if (model == AiModel.CHATGPT) {
                binding.tvProvider.text = "Coming Soon"
                binding.tvProvider.setTextColor(binding.root.resources.getColor(R.color.error, null))
                binding.tvStatusBadge.text = "● Offline"
                binding.tvStatusBadge.setTextColor(binding.root.resources.getColor(R.color.error, null))
                binding.root.isEnabled = false
                binding.root.alpha = 0.5f
                binding.btnOpenChat.isEnabled = false
                binding.btnOpenChat.alpha = 0.5f
            } else {
                binding.tvProvider.text = "by ${model.providerName}"
                binding.tvProvider.setTextColor(binding.root.resources.getColor(R.color.on_surface_variant, null))
                binding.tvStatusBadge.text = "● Online"
                binding.tvStatusBadge.setTextColor(binding.root.resources.getColor(R.color.secondary, null))
                binding.root.isEnabled = true
                binding.root.alpha = 1.0f
                binding.btnOpenChat.isEnabled = true
                binding.btnOpenChat.alpha = 1.0f
                binding.root.setOnClickListener { onClick(model) }
                binding.btnOpenChat.setOnClickListener { onClick(model) }
            }
        }

        private fun getEmoji(model: AiModel): String {
            return when (model) {
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
        }
    }

    private class AiModelDiffCallback : DiffUtil.ItemCallback<AiModel>() {
        override fun areItemsTheSame(oldItem: AiModel, newItem: AiModel): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: AiModel, newItem: AiModel): Boolean {
            return oldItem == newItem
        }
    }
}
