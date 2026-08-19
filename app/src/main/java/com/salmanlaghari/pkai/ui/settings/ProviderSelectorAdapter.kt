package com.salmanlaghari.pkai.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.model.LlmProvider
import com.salmanlaghari.pkai.databinding.ItemProviderSelectBinding

/**
 * Premium provider selector list used in Settings → AI.
 *
 * Each row is an elevated glass card showing the provider logo, name and tagline.
 * The selected provider is highlighted with the accent colour + a checkmark, and a
 * subtle scale/fade animation plays on tap for tactile feedback.
 */
class ProviderSelectorAdapter(
    private val onSelect: (LlmProvider) -> Unit
) : ListAdapter<LlmProvider, ProviderSelectorAdapter.ViewHolder>(ProviderDiffCallback()) {

    private var selectedId: String = ""

    fun setSelected(providerId: String) {
        val previous = selectedId
        selectedId = providerId
        // Re-bind only the rows whose selected state changed.
        currentList.forEachIndexed { index, provider ->
            if (provider.id == previous || provider.id == providerId) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProviderSelectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemProviderSelectBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(provider: LlmProvider) {
            val selected = provider.id == selectedId

            binding.tvLogo.text = provider.logoEmoji
            binding.tvName.text = provider.displayName
            binding.tvTagline.text = provider.tagline

            val accent = ContextCompat.getColor(binding.root.context, R.color.electric_blue_glow)
            val selectedBg = ContextCompat.getColor(binding.root.context, R.color.glass_elevated)
            val defaultBg = ContextCompat.getColor(binding.root.context, R.color.glass_background)

            binding.cardProvider.strokeColor = if (selected) accent else ContextCompat.getColor(binding.root.context, R.color.glass_stroke)
            binding.cardProvider.setCardBackgroundColor(if (selected) selectedBg else defaultBg)
            binding.tvName.setTextColor(if (selected) accent else ContextCompat.getColor(binding.root.context, R.color.white))
            binding.ivCheck.visibility = if (selected) android.view.View.VISIBLE else android.view.View.GONE

            binding.root.setOnClickListener {
                // Smooth tap animation: scale down briefly then back up.
                binding.root.animate()
                    .scaleX(0.97f)
                    .scaleY(0.97f)
                    .setDuration(80)
                    .withEndAction {
                        binding.root.animate().scaleX(1f).scaleY(1f).duration = 120
                    }
                    .start()
                onSelect(provider)
            }
        }
    }

    private class ProviderDiffCallback : DiffUtil.ItemCallback<LlmProvider>() {
        override fun areItemsTheSame(oldItem: LlmProvider, newItem: LlmProvider): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: LlmProvider, newItem: LlmProvider): Boolean =
            oldItem == newItem
    }
}
