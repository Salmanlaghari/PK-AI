package com.salmanlaghari.pkai.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.model.ChatMessage
import com.salmanlaghari.pkai.data.model.FreeAiModel
import com.salmanlaghari.pkai.data.repository.CodeExecutionResult
import com.salmanlaghari.pkai.databinding.ItemChatAiBinding
import com.salmanlaghari.pkai.databinding.ItemChatUserBinding
import com.salmanlaghari.pkai.databinding.ItemCodeBlockBinding
import com.salmanlaghari.pkai.util.CodeBlockParser
import com.salmanlaghari.pkai.util.ImageLoadHelper
import com.salmanlaghari.pkai.util.MarkdownImageParser
import com.salmanlaghari.pkai.util.MessageSegment
import com.salmanlaghari.pkai.util.TtsHelper

class ChatAdapter(
    private val onRunCode: ((source: String, lang: String, onResult: (CodeExecutionResult) -> Unit) -> Unit)? = null
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatDiffCallback()) {

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
            AiMessageViewHolder(binding, onRunCode)
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
            binding.ivUserFileIcon.setImageResource(R.drawable.ic_tools)
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

    class AiMessageViewHolder(
        private val binding: ItemChatAiBinding,
        private val onRunCode: ((source: String, lang: String, onResult: (CodeExecutionResult) -> Unit) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

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

            // Split the response into visible text + markdown image embeds
            val imageParsed = MarkdownImageParser.parse(message.content)

            // Parse code blocks
            val segments = CodeBlockParser.parseSegments(imageParsed.text)
            val textBuilder = StringBuilder()
            binding.layoutCodeBlocks.removeAllViews()
            binding.layoutCodeBlocks.visibility = View.GONE

            var hasCodeBlocks = false

            segments.forEach { segment ->
                when (segment) {
                    is MessageSegment.Text -> {
                        if (textBuilder.isNotEmpty()) textBuilder.append("\n\n")
                        textBuilder.append(segment.content)
                    }
                    is MessageSegment.CodeBlock -> {
                        hasCodeBlocks = true
                        val blockBinding = ItemCodeBlockBinding.inflate(
                            LayoutInflater.from(binding.root.context),
                            binding.layoutCodeBlocks,
                            true
                        )

                        val rawLangUpper = segment.rawLanguage.uppercase()
                        blockBinding.tvCodeLang.text = rawLangUpper
                        blockBinding.tvCodeContent.text = segment.code

                        // Copy code button
                        blockBinding.btnCopyCode.setOnClickListener {
                            val clipboard = binding.root.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Code", segment.code)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(binding.root.context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                        }

                        // Run code button
                        val heLang = segment.hackerEarthLang
                        if (heLang != null && onRunCode != null) {
                            blockBinding.btnRunCode.visibility = View.VISIBLE
                            blockBinding.btnRunCode.text = "▶ Run Code"
                            blockBinding.btnRunCode.isEnabled = true

                            blockBinding.btnRunCode.setOnClickListener {
                                blockBinding.btnRunCode.isEnabled = false
                                blockBinding.layoutRunLoading.visibility = View.VISIBLE
                                blockBinding.layoutRunOutputPanel.visibility = View.GONE

                                onRunCode.invoke(segment.code, heLang) { result ->
                                    blockBinding.btnRunCode.isEnabled = true
                                    blockBinding.layoutRunLoading.visibility = View.GONE
                                    blockBinding.layoutRunOutputPanel.visibility = View.VISIBLE

                                    when (result) {
                                        is CodeExecutionResult.Success -> {
                                            blockBinding.tvOutputStatusBadge.text = "✓ ACCEPTED (${result.runStatus})"
                                            blockBinding.tvOutputStatusBadge.setBackgroundColor(Color.parseColor("#103828"))
                                            blockBinding.tvOutputStatusBadge.setTextColor(Color.parseColor("#00E676"))
                                            blockBinding.tvOutputMetrics.text = "Time: ${String.format("%.3f", result.timeUsed)}s | Memory: ${result.memoryUsed} KB"
                                            blockBinding.tvOutputContent.text = result.stdout
                                            blockBinding.tvOutputContent.setTextColor(Color.parseColor("#00E5FF"))

                                            if (!result.stderr.isNullOrBlank()) {
                                                blockBinding.layoutErrorPanel.visibility = View.VISIBLE
                                                blockBinding.tvErrorContent.text = result.stderr
                                            } else {
                                                blockBinding.layoutErrorPanel.visibility = View.GONE
                                            }
                                            blockBinding.tvQuotaWarning.text = "Free Quota: ${result.remainingQuota}/1000 remaining"
                                        }
                                        is CodeExecutionResult.CompileError -> {
                                            blockBinding.tvOutputStatusBadge.text = "✗ COMPILATION ERROR"
                                            blockBinding.tvOutputStatusBadge.setBackgroundColor(Color.parseColor("#3D1016"))
                                            blockBinding.tvOutputStatusBadge.setTextColor(Color.parseColor("#FF5252"))
                                            blockBinding.tvOutputMetrics.text = "Failed"
                                            blockBinding.tvOutputContent.text = "(Compilation failed - see details below)"
                                            blockBinding.tvOutputContent.setTextColor(Color.parseColor("#A0AEC0"))
                                            blockBinding.layoutErrorPanel.visibility = View.VISIBLE
                                            blockBinding.tvErrorContent.text = result.compileErrorDetails
                                            blockBinding.tvQuotaWarning.text = "Quota unchanged on compile error"
                                        }
                                        is CodeExecutionResult.Error -> {
                                            blockBinding.tvOutputStatusBadge.text = "⚠️ ERROR"
                                            blockBinding.tvOutputStatusBadge.setBackgroundColor(Color.parseColor("#3D2610"))
                                            blockBinding.tvOutputStatusBadge.setTextColor(Color.parseColor("#FFAB40"))
                                            blockBinding.tvOutputMetrics.text = "Failed"
                                            blockBinding.tvOutputContent.text = result.message
                                            blockBinding.tvOutputContent.setTextColor(Color.parseColor("#FFAB40"))
                                            blockBinding.layoutErrorPanel.visibility = View.GONE
                                            blockBinding.tvQuotaWarning.text = "Check settings or network"
                                        }
                                        is CodeExecutionResult.QuotaExceeded -> {
                                            blockBinding.tvOutputStatusBadge.text = "⚠️ QUOTA EXCEEDED"
                                            blockBinding.tvOutputStatusBadge.setBackgroundColor(Color.parseColor("#3D1016"))
                                            blockBinding.tvOutputStatusBadge.setTextColor(Color.parseColor("#FF5252"))
                                            blockBinding.tvOutputMetrics.text = "0/1000 remaining"
                                            blockBinding.tvOutputContent.text = "You have reached the free quota limit of 1000 requests."
                                            blockBinding.tvOutputContent.setTextColor(Color.parseColor("#FF5252"))
                                            blockBinding.layoutErrorPanel.visibility = View.GONE
                                            blockBinding.tvQuotaWarning.text = "Quota resets daily"
                                        }
                                        is CodeExecutionResult.NoNetwork -> {
                                            blockBinding.tvOutputStatusBadge.text = "📡 NO INTERNET"
                                            blockBinding.tvOutputStatusBadge.setBackgroundColor(Color.parseColor("#383210"))
                                            blockBinding.tvOutputStatusBadge.setTextColor(Color.parseColor("#FFD700"))
                                            blockBinding.tvOutputMetrics.text = "Offline"
                                            blockBinding.tvOutputContent.text = "Please check your internet connection and try again."
                                            blockBinding.tvOutputContent.setTextColor(Color.parseColor("#FFD700"))
                                            blockBinding.layoutErrorPanel.visibility = View.GONE
                                            blockBinding.tvQuotaWarning.text = "Network required for execution"
                                        }
                                    }
                                }
                            }
                        } else {
                            blockBinding.btnRunCode.visibility = View.GONE
                        }
                    }
                }
            }

            if (hasCodeBlocks) {
                binding.layoutCodeBlocks.visibility = View.VISIBLE
            }

            val visibleText = textBuilder.toString().ifBlank { imageParsed.text.ifBlank { " " } }
            binding.tvAiMessage.text = visibleText

            // Reset image region
            binding.layoutAiImages.removeAllViews()
            binding.layoutAiImages.visibility = View.GONE
            binding.tvAiImageError.visibility = View.GONE

            if (imageParsed.images.isNotEmpty()) {
                binding.layoutAiImages.visibility = View.VISIBLE
                var anyError = false
                val density = binding.root.resources.displayMetrics.density
                imageParsed.images.forEach { image ->
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
                    ) {
                        anyError = true
                        imageView.visibility = View.GONE
                    }
                }
                if (anyError) binding.tvAiImageError.visibility = View.VISIBLE
            }

            // TTS button
            binding.btnTts.setOnClickListener {
                val context = binding.root.context
                if (TtsHelper.isSpeaking()) {
                    TtsHelper.stop()
                    binding.btnTts.setImageResource(android.R.drawable.ic_btn_speak_now)
                } else {
                    val lang = TtsHelper.detectLanguage(message.content)
                    binding.btnTts.setImageResource(android.R.drawable.ic_media_pause)
                    TtsHelper.speak(
                        context = context,
                        text = message.content,
                        lang = lang,
                        onComplete = {
                            binding.btnTts.setImageResource(android.R.drawable.ic_btn_speak_now)
                        },
                        onError = {
                            binding.btnTts.setImageResource(android.R.drawable.ic_btn_speak_now)
                        }
                    )
                }
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
