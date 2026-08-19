package com.salmanlaghari.pkai.ui.home

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salmanlaghari.pkai.data.model.ChatMessage
import com.salmanlaghari.pkai.data.model.FreeAiModel
import com.salmanlaghari.pkai.databinding.ItemChatAiBinding
import com.salmanlaghari.pkai.databinding.ItemChatUserBinding
import com.salmanlaghari.pkai.util.ImageLoadHelper
import com.salmanlaghari.pkai.util.MarkdownImageParser

class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isUser) VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            val binding = ItemChatUserBinding.inflate(inflater, parent, false)
            UserMessageViewHolder(binding)
        } else {
            val binding = ItemChatAiBinding.inflate(inflater, parent, false)
            AiMessageViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is UserMessageViewHolder) {
            holder.bind(message)
        } else if (holder is AiMessageViewHolder) {
            holder.bind(message)
        }
    }

    class UserMessageViewHolder(private val binding: ItemChatUserBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            binding.tvUserMessage.text = message.content

            val hasAttachment = !message.attachmentUri.isNullOrBlank()
            binding.layoutUserAttachment.visibility = if (hasAttachment) View.VISIBLE else View.GONE
            binding.ivUserAttachment.visibility = View.GONE
            binding.layoutUserFileChip.visibility = View.GONE

            if (hasAttachment) {
                val uri = message.attachmentUri!!
                val type = message.attachmentType ?: "file"
                if (type == "image") {
                    binding.ivUserAttachment.visibility = View.VISIBLE
                    ImageLoadHelper.load(
                        binding.root.context,
                        uri,
                        binding.ivUserAttachment
                    ) {
                        binding.ivUserAttachment.visibility = View.GONE
                        binding.layoutUserFileChip.visibility = View.VISIBLE
                    }
                } else if (type == "video") {
                    val thumb = videoThumbnail(uri)
                    if (thumb != null) {
                        binding.ivUserAttachment.setImageBitmap(thumb)
                        binding.ivUserAttachment.visibility = View.VISIBLE
                    } else {
                        showFileChip(message, type)
                    }
                } else {
                    showFileChip(message, type)
                }
            }
        }

        private fun showFileChip(message: ChatMessage, type: String) {
            binding.layoutUserFileChip.visibility = View.VISIBLE
            binding.ivUserFileIcon.setImageResource(
                when (type) {
                    "pdf" -> android.R.drawable.ic_menu_agenda
                    "audio" -> android.R.drawable.ic_btn_speak_now
                    "video" -> android.R.drawable.ic_media_play
                    else -> android.R.drawable.ic_menu_attachment
                }
            )
            binding.tvUserFileName.text = "${message.attachmentName ?: "Attachment"}"
        }

        private fun videoThumbnail(uri: String): Bitmap? = runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(binding.root.context, android.net.Uri.parse(uri))
            val frame = retriever.frameAtTime
            retriever.release()
            frame
        }.getOrNull()
    }

    class AiMessageViewHolder(private val binding: ItemChatAiBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            val label = message.modelUsed
            if (!label.isNullOrEmpty()) {
                val displayName = label.removePrefix(FreeAiModel.LABEL_PREFIX)
                binding.tvAiModelTag.text =
                    if (label == "Free Public AI") label else "Powered by $displayName"
                binding.tvAiModelTag.visibility = View.VISIBLE
            } else {
                binding.tvAiModelTag.visibility = View.GONE
            }

            // Split the response into visible text + any markdown image embeds.
            val parsed = MarkdownImageParser.parse(message.content)
            binding.tvAiMessage.text = parsed.text.ifBlank { " " }

            // Reset the image region for this bind.
            binding.layoutAiImages.removeAllViews()
            binding.layoutAiImages.visibility = View.GONE
            binding.tvAiImageError.visibility = View.GONE

            if (parsed.images.isNotEmpty()) {
                binding.layoutAiImages.visibility = View.VISIBLE
                var anyError = false
                val density = binding.root.resources.displayMetrics.density
                parsed.images.forEach { image ->
                    val imageView = ImageView(binding.root.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            (160 * density).toInt()
                        ).apply { bottomMargin = (8 * density).toInt() }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        adjustViewBounds = true
                    }
                    binding.layoutAiImages.addView(imageView)
                    ImageLoadHelper.load(
                        context = binding.root.context,
                        source = image.source,
                        imageView = imageView
                    ) { anyError = true }
                }
                if (anyError) binding.tvAiImageError.visibility = View.VISIBLE
            }
        }
    }

    private class ChatDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
}
