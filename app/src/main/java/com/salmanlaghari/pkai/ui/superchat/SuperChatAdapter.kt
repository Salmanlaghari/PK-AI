package com.salmanlaghari.pkai.ui.superchat

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.model.ChatMessage
import com.salmanlaghari.pkai.data.model.FreeAiModel
import com.salmanlaghari.pkai.data.repository.CodeExecutionResult
import com.salmanlaghari.pkai.ui.chat.CodeBlockBinder
import com.salmanlaghari.pkai.util.ImageLoadHelper
import com.salmanlaghari.pkai.util.MarkdownImageParser
import com.salmanlaghari.pkai.util.SpriteSheetLoader
import java.io.File

/**
 * Renders the Super Chat conversation with professional animations:
 * - User bubbles slide in from the right, AI 3D cards flip in with a
 *   rotationX entrance + one-shot shimmer sweep.
 * - A typing indicator (bouncing dots) is shown while the AI generates.
 * - Image attachments render as thumbnails (tap → fullscreen), voice notes
 *   as playable bubbles.
 * - Song search results render as a dedicated rich song card
 *   ([R.layout.item_song_card]) with artwork, title, artist and a play
 *   button — never plain text.
 *
 * Entrance animations run only once per message id — rebinding on scroll
 * never replays them.
 */
class SuperChatAdapter(
    private val onSpeak: (ChatMessage) -> Unit,
    private val onCopy: (ChatMessage) -> Unit,
    private val onFavorite: (ChatMessage) -> Unit,
    private val onShare: (ChatMessage) -> Unit,
    private val onImageClick: (String) -> Unit,
    /**
     * Runs a ```code``` snippet (Home screen). Null hides the Run button —
     * Super Chat shows code blocks with Copy only.
     */
    private val onRunCode: ((source: String, lang: String, onResult: (CodeExecutionResult) -> Unit) -> Unit)? = null
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
    private var pausedMessageId: String? = null

    /** Streaming player for song cards when no host callback handles playback. */
    private var songPlayer: MediaPlayer? = null

    /** Callback when the song play button is tapped (handled by Fragment). */
    var onSongPlayClicked: ((ChatMessage) -> Unit)? = null

    /** Id of the song message currently streaming. */
    var playingSongId: String? = null

    /** True when the current song is paused (shows ▶ to resume). */
    var isSongPaused: Boolean = false

    /**
     * LRU in-memory artwork cache (max 20 entries) — evicts oldest, never
     * wholesale-wipes.
     */
    private val artworkCache = object : LinkedHashMap<String, android.graphics.Bitmap>(20, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, android.graphics.Bitmap>
        ): Boolean = size > 20
    }

    /** Shared background executor for remote artwork downloads. */
    private val artworkExecutor: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newFixedThreadPool(2)

    /** Separate executor for local thumbnails so they never queue behind downloads. */
    private val thumbnailExecutor: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newSingleThreadExecutor()

    /** Loads a remote artwork URL into an ImageView (background thread + LRU cache). */
    private fun loadArtwork(url: String, target: ImageView) {
        if (url.isBlank()) return
        // Tag FIRST so any in-flight download for a previous bind can't win
        target.tag = url
        synchronized(artworkCache) {
            artworkCache[url]?.let { target.setImageBitmap(it); return }
        }
        // Guard: executor may be shut down after releasePlayer()
        if (artworkExecutor.isShutdown) return
        try {
            artworkExecutor.execute {
            var conn: java.net.HttpURLConnection? = null
            try {
                conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                if (conn.responseCode !in 200..299) return@execute
                // Bounded read: stream into a capped buffer so a hostile URL
                // can't allocate unbounded heap before the size check.
                val maxBytes = 2 * 1024 * 1024 // 2MB cap
                val buffer = java.io.ByteArrayOutputStream()
                val tmp = ByteArray(32 * 1024)
                var total = 0
                conn.inputStream.use { ins ->
                    while (true) {
                        val n = ins.read(tmp)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) return@execute // too large, skip
                        buffer.write(tmp, 0, n)
                    }
                }
                val bytes = buffer.toByteArray()
                // Downsample: song art is shown at ~56dp
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val sample = coerceSampleSize(bounds.outWidth, bounds.outHeight, 112, 112)
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                if (bmp != null) {
                    synchronized(artworkCache) { artworkCache[url] = bmp }
                    target.post { if (target.tag == url) target.setImageBitmap(bmp) }
                }
            } catch (_: Exception) { /* keep placeholder */ }
            finally { conn?.disconnect() }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { /* shut down */ }
    }

    /** Computes an inSampleSize that keeps the bitmap near target dimensions. */
    private fun coerceSampleSize(w: Int, h: Int, targetW: Int, targetH: Int): Int {
        if (w <= 0 || h <= 0) return 1
        var sample = 1
        while (w / (sample * 2) >= targetW && h / (sample * 2) >= targetH) sample *= 2
        return sample.coerceAtLeast(1)
    }

    fun setStickers(map: Map<String, Int>) {
        stickers = map
        notifyDataSetChanged()
    }

    fun releasePlayer() {
        voicePlayer?.release()
        voicePlayer = null
        playingMessageId = null
        pausedMessageId = null
        try { songPlayer?.stop() } catch (_: Exception) { }
        songPlayer?.release()
        songPlayer = null
        playingSongId = null
        isSongPaused = false
        // Shut down background executors so no threads leak per adapter instance
        try { artworkExecutor.shutdownNow() } catch (_: Exception) { }
        try { thumbnailExecutor.shutdownNow() } catch (_: Exception) { }
    }

    // ── ViewHolders ──────────────────────────────────────────────────

    abstract class BaseHolder(view: View) : RecyclerView.ViewHolder(view)

    inner class MessageHolder(view: View) : BaseHolder(view) {
        private val userRow: View = view.findViewById(R.id.userRow)
        private val aiRow: View = view.findViewById(R.id.aiRow)
        private val aiCard: View = view.findViewById(R.id.aiCard)
        private val pkAiVisualHeader: View = view.findViewById(R.id.pkAiVisualHeader)
        private val shimmerView: View = view.findViewById(R.id.shimmerView)
        private val tvUserMessage: TextView = view.findViewById(R.id.tvUserMessage)
        private val tvAiName: TextView = view.findViewById(R.id.tvAiName)
        private val tvAiMessage: TextView = view.findViewById(R.id.tvAiMessage)
        private val ivAiSticker: ImageView = view.findViewById(R.id.ivAiSticker)
        private val layoutAiImages: LinearLayout = view.findViewById(R.id.layoutAiImages)
        private val tvAiImageError: TextView = view.findViewById(R.id.tvAiImageError)
        private val layoutCodeBlocks: LinearLayout = view.findViewById(R.id.layoutCodeBlocks)
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

                // Model tag (e.g. "Groq"). Hidden when the message has no model
                // (e.g. Home system messages like "Switched to Gemini"); the
                // internal "Free · " tier prefix is stripped before display.
                val modelUsed = message.modelUsed?.takeIf { it.isNotBlank() }
                if (modelUsed == null) {
                    tvAiName.visibility = View.GONE
                } else {
                    tvAiName.visibility = View.VISIBLE
                    tvAiName.text = modelUsed.removePrefix(FreeAiModel.LABEL_PREFIX)
                }

                // Mood sticker beside every AI reply; hidden when the message
                // has none (e.g. on Home, which doesn't do mood poses).
                val stickerIndex = stickers[message.id]
                if (stickerIndex != null) {
                    ivAiSticker.visibility = View.VISIBLE
                    ivAiSticker.setImageBitmap(
                        SpriteSheetLoader.getSticker(itemView.context, stickerIndex)
                    )
                } else {
                    ivAiSticker.visibility = View.GONE
                }

                // PK-AI visual result header for PK-AI mode replies ✨
                pkAiVisualHeader.visibility =
                    if (message.modelUsed == com.salmanlaghari.pkai.util.PkAiAssistant.PK_AI_LABEL)
                        View.VISIBLE else View.GONE

                // Generated-image markdown renders inline; ```code``` blocks get
                // the shared runnable cards; the rest stays as bubble text.
                val imageParsed = MarkdownImageParser.parse(message.content)
                bindMarkdownImages(message.id, imageParsed.images)
                val visibleText = CodeBlockBinder.bind(
                    itemView.context,
                    layoutCodeBlocks,
                    imageParsed.text,
                    onRunCode
                )
                tvAiMessage.text = visibleText.ifBlank { " " }

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

        /**
         * Renders markdown image embeds (e.g. generated pictures) inline below
         * the bubble text. Tap opens the fullscreen viewer.
         */
        private fun bindMarkdownImages(
            messageId: String,
            images: List<MarkdownImageParser.MarkdownImage>
        ) {
            // Tag guard: a recycled holder must never show another message's
            // images or a stale error from an in-flight load.
            layoutAiImages.tag = messageId
            layoutAiImages.removeAllViews()
            tvAiImageError.visibility = View.GONE
            if (images.isEmpty()) {
                layoutAiImages.visibility = View.GONE
                return
            }
            layoutAiImages.visibility = View.VISIBLE
            val density = itemView.resources.displayMetrics.density
            images.forEach { image ->
                val imageView = ImageView(itemView.context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (160 * density).toInt()
                    ).apply { bottomMargin = (8 * density).toInt() }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    adjustViewBounds = true
                    setOnClickListener { onImageClick(image.source) }
                }
                layoutAiImages.addView(imageView)
                ImageLoadHelper.load(itemView.context, image.source, imageView) {
                    if (layoutAiImages.tag == messageId) {
                        imageView.visibility = View.GONE
                        tvAiImageError.visibility = View.VISIBLE
                    }
                }
            }
        }

        private fun bindUserContent(message: ChatMessage) {
            when (message.attachmentType) {
                "image" -> {
                    voiceUserRow.visibility = View.GONE
                    ivUserImage.visibility = View.VISIBLE
                    // Keep the user's caption above the image when one was typed.
                    if (message.content.isNotBlank()) {
                        tvUserMessage.visibility = View.VISIBLE
                        tvUserMessage.text = message.content
                    } else {
                        tvUserMessage.visibility = View.GONE
                    }
                    loadThumbnail(message.attachmentUri, ivUserImage)
                    ivUserImage.setOnClickListener {
                        message.attachmentUri?.let { onImageClick(it) }
                    }
                }
                "audio" -> {
                    ivUserImage.visibility = View.GONE
                    voiceUserRow.visibility = View.VISIBLE
                    // Keep the user's caption above the voice note when one was typed.
                    if (message.content.isNotBlank()) {
                        tvUserMessage.visibility = View.VISIBLE
                        tvUserMessage.text = message.content
                    } else {
                        tvUserMessage.visibility = View.GONE
                    }
                    val playing = playingMessageId == message.id && pausedMessageId != message.id
                    btnUserVoicePlay.text = if (playing) "⏸" else "▶"
                    tvUserVoiceDuration.text = message.attachmentName ?: "🎤"
                    btnUserVoicePlay.setOnClickListener { toggleVoice(message) }
                }
                else -> {
                    tvUserMessage.visibility = View.VISIBLE
                    ivUserImage.visibility = View.GONE
                    voiceUserRow.visibility = View.GONE
                    // File attachments (video/pdf/…) show a label when there is no typed text.
                    tvUserMessage.text = message.content.ifBlank {
                        val emoji = when (message.attachmentType) {
                            "video" -> "🎥"
                            "audio" -> "🎵"
                            "pdf" -> "📄"
                            else -> "📎"
                        }
                        "$emoji ${message.attachmentName ?: "Attachment"}"
                    }
                }
            }
        }

        private fun loadThumbnail(uri: String?, target: ImageView) {
            if (uri == null) return
            // Tag guard: recycled rows must not show another message's image
            target.tag = uri
            target.setImageDrawable(null)
            // Guard: executor may be shut down after releasePlayer()
            if (thumbnailExecutor.isShutdown) return
            // Decode off the main thread on its own executor (never queued
            // behind network artwork downloads).
            try {
            thumbnailExecutor.execute {
                try {
                    // Sample to ~512px: crisp in the list, full image kept for
                    // the fullscreen viewer (loaded separately on tap).
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    try {
                        target.context.contentResolver
                            .openInputStream(android.net.Uri.parse(uri))?.use { ins ->
                                BitmapFactory.decodeStream(ins, null, opts)
                            }
                    } catch (_: Exception) { }
                    val sample = coerceSampleSize(opts.outWidth, opts.outHeight, 512, 512)
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bmp = try {
                        target.context.contentResolver
                            .openInputStream(android.net.Uri.parse(uri))?.use { ins ->
                                BitmapFactory.decodeStream(ins, null, decodeOpts)
                            }
                    } catch (_: Exception) {
                        // Try as plain file path fallback
                        try {
                            val f = File(uri)
                            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath, decodeOpts)
                            else null
                        } catch (_: Exception) { null }
                    }
                    if (bmp != null) target.post {
                        if (target.tag == uri) target.setImageBitmap(bmp)
                    }
                } catch (_: Exception) { /* keep placeholder */ }
            }
            } catch (_: java.util.concurrent.RejectedExecutionException) { /* shut down */ }
        }

        private fun toggleVoice(message: ChatMessage) {
            val uri = message.attachmentUri ?: return
            if (playingMessageId == message.id && voicePlayer != null) {
                if (voicePlayer?.isPlaying == true) {
                    voicePlayer?.pause()
                    pausedMessageId = message.id
                    btnUserVoicePlay.text = "▶"
                } else {
                    voicePlayer?.start()
                    pausedMessageId = null
                    btnUserVoicePlay.text = "⏸"
                }
                return
            }
            try {
                voicePlayer?.release()
                pausedMessageId = null
                voicePlayer = MediaPlayer().apply {
                    setDataSource(itemView.context, android.net.Uri.parse(uri))
                    prepare()
                    setOnCompletionListener {
                        playingMessageId = null
                        pausedMessageId = null
                        btnUserVoicePlay.text = "▶"
                    }
                    start()
                }
                playingMessageId = message.id
                pausedMessageId = null
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

    /**
     * Dedicated rich song card ([R.layout.item_song_card]) for song search
     * results: artwork thumbnail (music-note placeholder until it loads),
     * title, artist, and a play button.
     *
     * Playback: when a host sets [onSongPlayClicked] (Super Chat fragment
     * owns streaming there), the tap is delegated to it. Otherwise the card
     * plays the stream itself with MediaPlayer; when there is no audio URL it
     * opens the track page in the browser instead.
     */
    inner class SongCardHolder(view: View) : BaseHolder(view) {
        private val cardRoot: View = view.findViewById(R.id.songCardRoot)
        private val ivArt: ImageView = view.findViewById(R.id.ivCardArt)
        private val tvTitle: TextView = view.findViewById(R.id.tvCardTitle)
        private val tvArtist: TextView = view.findViewById(R.id.tvCardArtist)
        private val btnPlay: TextView = view.findViewById(R.id.btnCardPlay)

        fun bind(item: Item.Message) {
            val message = item.message
            val isNew = animatedIds.add(message.id)
            val parts = (message.attachmentName ?: "").split("|||")
            val title = parts.getOrElse(0) { "Unknown Song" }.ifBlank { "Unknown Song" }
            val artist = parts.getOrElse(1) { "Unknown Artist" }.ifBlank { "Unknown Artist" }
            val artwork = parts.getOrElse(2) { "" }
            // Reset to the music-note placeholder first — a recycled holder
            // must never flash another song's artwork.
            ivArt.setImageResource(R.drawable.ic_music_note)
            tvTitle.text = title
            tvArtist.text = artist
            loadArtwork(artwork, ivArt)
            btnPlay.text =
                if (playingSongId == message.id && !isSongPaused) "⏸" else "▶"
            btnPlay.setOnClickListener { onPlayTapped(message) }
            if (isNew) {
                cardRoot.alpha = 0f
                cardRoot.translationY = 40f
                cardRoot.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(320)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
        }

        private fun onPlayTapped(message: ChatMessage) {
            // Super Chat fragment owns streaming playback — delegate to it.
            val host = onSongPlayClicked
            if (host != null) {
                host(message)
                return
            }
            // No host handler (e.g. Home screen): play inline in the adapter.
            toggleLocalPlayback(message)
        }

        /** Self-contained streaming playback, used when no host callback is set. */
        private fun toggleLocalPlayback(message: ChatMessage) {
            val ctx = itemView.context
            val audioUrl = message.attachmentUri
            if (audioUrl.isNullOrBlank()) {
                // No stream — fall back to opening the track page in the browser.
                val pageUrl = (message.attachmentName ?: "").split("|||").getOrElse(3) { "" }
                if (pageUrl.isNotBlank()) {
                    try {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                        Toast.makeText(ctx, "😔 Link nahi khul saka", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(ctx, "😔 Audio stream nahi mila", Toast.LENGTH_SHORT).show()
                }
                return
            }
            try {
                // Tapping the currently playing song toggles pause/resume.
                if (playingSongId == message.id && songPlayer != null) {
                    val player = songPlayer!!
                    if (player.isPlaying) {
                        player.pause()
                        isSongPaused = true
                    } else {
                        player.start()
                        isSongPaused = false
                    }
                    notifyDataSetChanged()
                    return
                }
                // Stop any previous song before starting a new one.
                try { songPlayer?.stop() } catch (_: Exception) { }
                songPlayer?.release()
                songPlayer = null
                isSongPaused = false
                Toast.makeText(ctx, "🎵 Loading song…", Toast.LENGTH_SHORT).show()
                val player = MediaPlayer()
                try {
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    player.setDataSource(audioUrl)
                    // Assign BEFORE prepareAsync so callbacks always see the field.
                    songPlayer = player
                    player.setOnPreparedListener {
                        it.start()
                        playingSongId = message.id
                        isSongPaused = false
                        notifyDataSetChanged()
                    }
                    player.setOnCompletionListener {
                        playingSongId = null
                        isSongPaused = false
                        notifyDataSetChanged()
                    }
                    player.setOnErrorListener { _, _, _ ->
                        Toast.makeText(ctx, "😔 Song play nahi ho saka", Toast.LENGTH_SHORT).show()
                        playingSongId = null
                        isSongPaused = false
                        notifyDataSetChanged()
                        true
                    }
                    player.prepareAsync()
                } catch (_: Exception) {
                    try { player.release() } catch (_: Exception) { }
                    if (songPlayer === player) songPlayer = null
                    Toast.makeText(ctx, "😔 Song play nahi ho saka", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(ctx, "😔 Song play nahi ho saka", Toast.LENGTH_SHORT).show()
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

    /** True for AI song-result messages, which get the dedicated song card. */
    private fun isSongCard(message: ChatMessage): Boolean =
        !message.isUser && message.attachmentType == ATTACHMENT_SONG

    override fun getItemViewType(position: Int): Int = when (val item = getItem(position)) {
        is Item.Message -> if (isSongCard(item.message)) VIEW_SONG_CARD else VIEW_MESSAGE
        Item.Typing -> VIEW_TYPING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPING ->
                TypingHolder(inflater.inflate(R.layout.item_typing_indicator, parent, false))
            VIEW_SONG_CARD ->
                SongCardHolder(inflater.inflate(R.layout.item_song_card, parent, false))
            else ->
                MessageHolder(inflater.inflate(R.layout.item_super_chat_message, parent, false))
        }
    }

    override fun onBindViewHolder(holder: BaseHolder, position: Int) {
        when (val item = getItem(position)) {
            is Item.Message -> when (holder) {
                is SongCardHolder -> holder.bind(item)
                is MessageHolder -> holder.bind(item)
                else -> throw IllegalStateException("Unexpected holder for chat message")
            }
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
        private const val VIEW_SONG_CARD = 2
        /** [ChatMessage.attachmentType] value marking a rich song result card. */
        const val ATTACHMENT_SONG = "song"

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
