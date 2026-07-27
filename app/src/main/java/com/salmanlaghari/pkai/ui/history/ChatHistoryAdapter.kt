package com.salmanlaghari.pkai.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.model.ChatHistoryItem
import com.salmanlaghari.pkai.databinding.ItemHistoryCardBinding
import com.salmanlaghari.pkai.databinding.ItemHistoryHeaderBinding

sealed class HistoryUiItem {
    data class Header(val title: String) : HistoryUiItem()
    data class Card(val item: ChatHistoryItem) : HistoryUiItem()
}

class ChatHistoryAdapter(
    private val onRename: (ChatHistoryItem) -> Unit,
    private val onPin: (ChatHistoryItem) -> Unit,
    private val onDelete: (ChatHistoryItem) -> Unit,
    private val onShare: (ChatHistoryItem) -> Unit,
    private val onClick: (ChatHistoryItem) -> Unit
) : ListAdapter<HistoryUiItem, RecyclerView.ViewHolder>(HistoryDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_CARD = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HistoryUiItem.Header -> VIEW_TYPE_HEADER
            is HistoryUiItem.Card -> VIEW_TYPE_CARD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            val binding = ItemHistoryHeaderBinding.inflate(inflater, parent, false)
            HeaderViewHolder(binding)
        } else {
            val binding = ItemHistoryCardBinding.inflate(inflater, parent, false)
            CardViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val uiItem = getItem(position)
        if (holder is HeaderViewHolder && uiItem is HistoryUiItem.Header) {
            holder.bind(uiItem)
        } else if (holder is CardViewHolder && uiItem is HistoryUiItem.Card) {
            holder.bind(uiItem.item)
        }
    }

    class HeaderViewHolder(private val binding: ItemHistoryHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: HistoryUiItem.Header) {
            binding.tvHeaderTitle.text = header.title
        }
    }

    inner class CardViewHolder(private val binding: ItemHistoryCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(chat: ChatHistoryItem) {
            binding.tvHistoryTitle.text = chat.title
            binding.tvHistorySubtitle.text = chat.lastMessage

            // Pin indicator visible
            binding.ivPinIndicator.visibility = if (chat.isPinned) View.VISIBLE else View.GONE

            binding.root.setOnClickListener { onClick(chat) }

            // Actions dropdown menu click callback
            binding.btnMoreActions.setOnClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menu.add(0, 1, 0, "✏️ Rename")
                popup.menu.add(0, 2, 0, if (chat.isPinned) "📍 Unpin" else "📌 Pin")
                popup.menu.add(0, 3, 0, "📤 Share")
                popup.menu.add(0, 4, 0, "🗑️ Delete")

                popup.setOnMenuItemClickListener { menuItem ->
                    when (itemMenuId(menuItem.itemId)) {
                        1 -> onRename(chat)
                        2 -> onPin(chat)
                        3 -> onShare(chat)
                        4 -> onDelete(chat)
                    }
                    true
                }
                popup.show()
            }
        }

        private fun itemMenuId(itemId: Int): Int = itemId
    }

    private class HistoryDiffCallback : DiffUtil.ItemCallback<HistoryUiItem>() {
        override fun areItemsTheSame(oldItem: HistoryUiItem, newItem: HistoryUiItem): Boolean {
            return if (oldItem is HistoryUiItem.Header && newItem is HistoryUiItem.Header) {
                oldItem.title == newItem.title
            } else if (oldItem is HistoryUiItem.Card && newItem is HistoryUiItem.Card) {
                oldItem.item.id == newItem.item.id
            } else {
                false
            }
        }

        override fun areContentsTheSame(oldItem: HistoryUiItem, newItem: HistoryUiItem): Boolean {
            return oldItem == newItem
        }
    }
}
