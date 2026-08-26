package com.salmanlaghari.pkai.ui.superchat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.model.ChatMessage
import com.salmanlaghari.pkai.util.SpriteSheetLoader

/**
 * Renders the Super Chat conversation. One item layout hosts both the user row and
 * the AI row; visibility is toggled per message so the list can flip efficiently.
 */
class SuperChatAdapter(
    private val onSpeak: (ChatMessage) -> Unit,
    private val onCopy: (ChatMessage) -> Unit,
    private val onFavorite: (ChatMessage) -> Unit,
    private val onShare: (ChatMessage) -> Unit
) : ListAdapter<ChatMessage, SuperChatAdapter.MessageViewHolder>(DIFF) {

    var favoriteContents: Set<String> = emptySet()

    /** Mood sticker index per message id — shown beside each AI reply. */
    private var stickers: Map<String, Int> = emptyMap()

    /** Replaces the sticker map and rebinds so new replies pick up their pose. */
    fun setStickers(map: Map<String, Int>) {
        stickers = map
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_super_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val userRow: View = view.findViewById(R.id.userRow)
        private val aiRow: View = view.findViewById(R.id.aiRow)
        private val tvUserMessage: TextView = view.findViewById(R.id.tvUserMessage)
        private val tvAiMessage: TextView = view.findViewById(R.id.tvAiMessage)
        private val tvUserLabel: TextView = view.findViewById(R.id.tvUserName)

        fun bind(message: ChatMessage) {
            if (message.isUser) {
                userRow.visibility = View.VISIBLE
                aiRow.visibility = View.GONE
                tvUserMessage.text = message.content
                tvUserLabel.text = "You"
            } else {
                userRow.visibility = View.GONE
                aiRow.visibility = View.VISIBLE
                tvAiMessage.text = message.content

                // Mood sticker beside this reply — stays visible in history.
                stickers[message.id]?.let { index ->
                    itemView.findViewById<ImageView>(R.id.ivAiSticker)
                        .setImageBitmap(SpriteSheetLoader.getSticker(itemView.context, index))
                }

                val fav = message.content in favoriteContents
                itemView.findViewById<TextView>(R.id.btnFav).text = if (fav) "💖" else "💜"
                itemView.findViewById<TextView>(R.id.btnSpeak).setOnClickListener { onSpeak(message) }
                itemView.findViewById<TextView>(R.id.btnCopy).setOnClickListener { onCopy(message) }
                itemView.findViewById<TextView>(R.id.btnFav).setOnClickListener { onFavorite(message) }
                itemView.findViewById<TextView>(R.id.btnShare).setOnClickListener { onShare(message) }
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) =
                oldItem == newItem
        }
    }
}
