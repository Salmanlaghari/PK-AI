package com.salmanlaghari.pkai.ui.superchat

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.model.ChatMessage
import com.salmanlaghari.pkai.util.SpriteSheetLoader
import java.io.File

/**
 * Renders the Super Chat conversation with professional animations:
 * - User bubbles slide in from the right, AI 3D cards flip in with a
 *   rotationX entrance + one-shot shimmer sweep.
 * - A typing indicator (bouncing dots) is shown while the AI generates.
 * - Image attachments render as thumbnails (tap → fullscreen), voice notes
 *   as playable bubbles.
 *
 * Entrance animations run only once per message id — rebinding on scroll
 * never replays them.
 */
class SuperChatAdapter(
    private val onSpeak: (ChatMessage) -> Unit,
    private val onCopy: (ChatMessage) -> Unit,
    private val onFavorite: (ChatMessage) -> Unit,
    private val onShare: (ChatMessage) -> Unit,
    private val onImageClick: (String) -> Unit
) : ListAdapter<SuperChatAdapter.Item, SuperChatAdapter.BaseHolder>(DIFF) {

    /** Adapter items: either a chat message or the typing indicator. */
    sealed interface Item {
        data class Message(val message: ChatMessage) : Item
        data object Typing : Item
    }

    var favoriteContents: Set<String> = emptySet()

    /** Mood sticker index per message id — shown beside each AI reply. */
    private var stickers: Map<String, Int> = emptyMap()

    /** Message ids that already played their entrance animation. */
    private val animatedIds = mutableSetOf<String>()

    private var voicePlayer: MediaPlayer? = null
    private var playingMessageId: String? = null

    fun setStickers(map: Map<String, Int>) {
        stickers = map
        notifyDataSetChanged()
    }

    fun releasePlayer() {
        voicePlayer?.release()
        voicePlayer = null
        playingMessageId = null
    }

    // ── ViewHolders ──────────────────────────────────────────────────

    abstract class BaseHolder(view: View) : RecyclerView.ViewHolder(view)

    inner class MessageHolder(view: View) : BaseHolder(view) {
        private val userRow: View = view.findViewById(R.id.userRow)
        private val aiRow: View = view.findViewById(R.id.aiRow)
        private val aiCard: View = view.findViewById(R.id.aiCard)
        private val shimmerView: View = view.findViewById(R.id.shimmerView)
        private val tvUserMessage: TextView = view.findViewById(R.id.tvUserMessage)
        private val tvAiMessage: TextView = view.findViewById(R.id.tvAiMessage)
        private val tvUserLabel: TextView = view.findViewById(R.id.tvUserName)
        private val ivUserImage: ImageView = view.findViewById(R.id.ivUserImage)
        private val voiceUserRow: View = view.findViewById(R.id.voiceUserRow)
        private val btnUserVoicePlay: TextView = view.findViewById(R.id.btnUserVoicePlay)
        private val tvUserVoiceDuration: TextView = view.findViewById(R.id.tvUserVoiceDuration)

        fun bind(item: Item.Message) {
            val message = item.message
            val isNew = animatedIds.add(message.id)

            if (message.isUser) {
                userRow.visibility = View.VISIBLE
                aiRow.visibility = View.GONE
                tvUserLabel.text = "You"
                bindUserContent(message)
                if (isNew) animateUserIn(userRow)
            } else {
                userRow.visibility = View.GONE
                aiRow.visibility = View.VISIBLE
                tvAiMessage.text = message.content

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

                if (isNew) {
                    animateAiCardIn(aiCard)
                    playShimmerOnce()
                }
            }
        }

        private fun bindUserContent(message: ChatMessage) {
            when (message.attachmentType) {
                "image" -> {
                    tvUserMessage.visibility = View.GONE
                    voiceUserRow.visibility = View.GONE
                    ivUserImage.visibility = View.VISIBLE
                    loadThumbnail(message.attachmentUri, ivUserImage)
                    ivUserImage.setOnClickListener {
                        message.attachmentUri?.let { onImageClick(it) }
                    }
                }
                "audio" -> {
                    tvUserMessage.visibility = View.GONE
                    ivUserImage.visibility = View.GONE
                    voiceUserRow.visibility = View.VISIBLE
                    val playing = playingMessageId == message.id
                    btnUserVoicePlay.text = if (playing) "⏸" else "▶"
                    tvUserVoiceDuration.text = message.attachmentName ?: "🎤"
                    btnUserVoicePlay.setOnClickListener { toggleVoice(message) }
                }
                else -> {
                    tvUserMessage.visibility = View.VISIBLE
                    ivUserImage.visibility = View.GONE
                    voiceUserRow.visibility = View.GONE
                    tvUserMessage.text = message.content
                }
            }
        }

        private fun loadThumbnail(uri: String?, target: ImageView) {
            if (uri == null) return
            try {
                target.context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { ins ->
                    target.setImageBitmap(BitmapFactory.decodeStream(ins))
                }
            } catch (_: Exception) {
                // Try as plain file path fallback
                try {
                    val f = File(uri)
                    if (f.exists()) target.setImageBitmap(BitmapFactory.decodeFile(f.absolutePath))
                } catch (_: Exception) { }
            }
        }

        private fun toggleVoice(message: ChatMessage) {
            val uri = message.attachmentUri ?: return
            if (playingMessageId == message.id) {
                voicePlayer?.pause()
                playingMessageId = null
                btnUserVoicePlay.text = "▶"
                return
            }
            try {
                voicePlayer?.release()
                voicePlayer = MediaPlayer().apply {
                    setDataSource(itemView.context, android.net.Uri.parse(uri))
                    prepare()
                    setOnCompletionListener {
                        playingMessageId = null
                        btnUserVoicePlay.text = "▶"
                    }
                    start()
                }
                playingMessageId = message.id
                btnUserVoicePlay.text = "⏸"
            } catch (_: Exception) { }
        }

        /** User bubble: slide from right + fade. */
        private fun animateUserIn(row: View) {
            row.translationX = 120f
            row.alpha = 0f
            row.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(280)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        /** AI 3D card: rotationX flip + scale + fade. */
        private fun animateAiCardIn(card: View) {
            card.cameraDistance = 12f * card.resources.displayMetrics.density * 160f
            card.rotationX = -70f
            card.scaleX = 0.92f
            card.scaleY = 0.92f
            card.alpha = 0f
            card.animate()
                .rotationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(380)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        /** One-shot shimmer sweep across the new AI card. */
        private fun playShimmerOnce() {
            shimmerView.post {
                val cardWidth = aiCard.width.toFloat()
                if (cardWidth <= 0f) return@post
                shimmerView.visibility = View.VISIBLE
                shimmerView.translationX = -120f
                shimmerView.animate()
                    .translationX(cardWidth + 120f)
                    .setDuration(650)
                    .setStartDelay(200)
                    .withEndAction { shimmerView.visibility = View.INVISIBLE }
                    .start()
            }
        }
    }

    inner class TypingHolder(view: View) : BaseHolder(view) {
        private val dots = listOf<View>(
            view.findViewById(R.id.dot1),
            view.findViewById(R.id.dot2),
            view.findViewById(R.id.dot3)
        )
        private val animators = mutableListOf<ObjectAnimator>()

        fun start() {
            stop()
            dots.forEachIndexed { i, dot ->
                ObjectAnimator.ofFloat(dot, "translationY", 0f, -12f, 0f).apply {
                    duration = 600
                    startDelay = (i * 150).toLong()
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                    animators.add(this)
                }
            }
        }

        fun stop() {
            animators.forEach { it.cancel() }
            animators.clear()
            dots.forEach { it.translationY = 0f }
        }
    }

    // ── Adapter ──────────────────────────────────────────────────────

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is Item.Message -> VIEW_MESSAGE
        Item.Typing -> VIEW_TYPING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPING) {
            TypingHolder(inflater.inflate(R.layout.item_typing_indicator, parent, false))
        } else {
            MessageHolder(inflater.inflate(R.layout.item_super_chat_message, parent, false))
        }
    }

    override fun onBindViewHolder(holder: BaseHolder, position: Int) {
        when (val item = getItem(position)) {
            is Item.Message -> (holder as MessageHolder).bind(item)
            Item.Typing -> (holder as TypingHolder).start()
        }
    }

    override fun onViewRecycled(holder: BaseHolder) {
        if (holder is TypingHolder) holder.stop()
        super.onViewRecycled(holder)
    }

    companion object {
        private const val VIEW_MESSAGE = 0
        private const val VIEW_TYPING = 1

        private val DIFF = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean =
                when {
                    oldItem is Item.Message && newItem is Item.Message ->
                        oldItem.message.id == newItem.message.id
                    oldItem is Item.Typing && newItem is Item.Typing -> true
                    else -> false
                }

            override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean =
                oldItem == newItem
        }
    }
}
