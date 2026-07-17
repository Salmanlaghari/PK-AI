package com.salmanlaghari.pkai.ui.chats

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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
            binding.tvProvider.text = "by ${model.providerName}"

            if (model == AiModel.CHATGPT) {
                binding.tvStatusBadge.text = "● Coming Soon"
                binding.tvStatusBadge.setTextColor(binding.root.context.resources.getColor(android.R.color.darker_gray, null))
                binding.tvName.setTextColor(binding.root.context.resources.getColor(android.R.color.darker_gray, null))
                binding.tvProvider.setTextColor(binding.root.context.resources.getColor(android.R.color.darker_gray, null))
                val disabledClick = {
                    android.widget.Toast.makeText(binding.root.context, "ChatGPT model is coming soon!", android.widget.Toast.LENGTH_SHORT).show()
                }
                binding.root.setOnClickListener { disabledClick() }
                binding.btnOpenChat.setOnClickListener { disabledClick() }
            } else {
                binding.tvStatusBadge.text = "● Online"
                binding.tvStatusBadge.setTextColor(binding.root.context.resources.getColor(com.salmanlaghari.pkai.R.color.electric_blue_glow, null))
                binding.tvName.setTextColor(binding.root.context.resources.getColor(com.salmanlaghari.pkai.R.color.white, null))
                binding.tvProvider.setTextColor(binding.root.context.resources.getColor(com.salmanlaghari.pkai.R.color.outline, null))

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
