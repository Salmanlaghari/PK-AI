package com.salmanlaghari.pkai.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.salmanlaghari.pkai.MainActivity
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.databinding.FragmentHomeBinding
import com.salmanlaghari.pkai.ui.voice.VoiceRecognitionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private var isGuest = false
    private var guestMessageCount = 0

    /** A file the user picked but hasn't sent yet. */
    private data class PendingAttachment(
        val type: String,
        val uri: String,
        val name: String,
        val mime: String
    )

    private var pendingAttachment: PendingAttachment? = null

    /** Speech-to-text helper for the inline voice button. */
    private var voiceHelper: VoiceRecognitionHelper? = null
    private var isVoiceListening = false

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceInput()
        else Toast.makeText(requireContext(), "Microphone permission is needed for voice input.", Toast.LENGTH_SHORT).show()
    }

    private val pickMedia =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@registerForActivityResult
            handlePicked(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val chatAdapter = ChatAdapter()
        binding.rvChatMessages.adapter = chatAdapter

        lifecycleScope.launch {
            preferencesManager.userSession.collect { session ->
                isGuest = session.isLoggedIn && session.isGuest
            }
        }
        lifecycleScope.launch {
            preferencesManager.guestMessageCount.collect { count ->
                guestMessageCount = count
            }
        }

        // Active provider chip — persistent indicator (Issue 1) and web-search toggle.
        binding.chipActiveProvider.setOnClickListener {
            when {
                viewModel.isImageMode.value -> Toast.makeText(
                    requireContext(),
                    "Image generation uses Hugging Face's SDXL model.",
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.isFreeMode.value -> Toast.makeText(
                    requireContext(),
                    "Web AI is a Premium feature — switch to Premium to enable.",
                    Toast.LENGTH_SHORT
                ).show()
                else -> viewModel.setWebSearchMode(!viewModel.webSearchMode.value)
            }
        }

        lifecycleScope.launch {
            viewModel.isFreeMode.collect { updateProviderChip() }
        }
        lifecycleScope.launch {
            viewModel.isImageMode.collect { updateProviderChip() }
        }
        lifecycleScope.launch {
            viewModel.effectiveProvider.collect { updateProviderChip() }
        }
        lifecycleScope.launch {
            viewModel.selectedFreeModel.collect { updateProviderChip() }
        }
        lifecycleScope.launch {
            viewModel.webSearchMode.collect { updateProviderChip() }
        }
        lifecycleScope.launch {
            viewModel.generatingLabel.collect { binding.tvTyping.text = it }
        }

        binding.btnTabPremium.setOnClickListener { viewModel.setFreeMode(false) }
        binding.btnTabFree.setOnClickListener { viewModel.setFreeMode(true) }
        binding.btnTabImage.setOnClickListener { viewModel.setImageMode(true) }

        setupFreeModelChips()

        lifecycleScope.launch {
            viewModel.chatMessages.collect { messages ->
                chatAdapter.submitList(messages) {
                    if (messages.isNotEmpty()) {
                        binding.rvChatMessages.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.isGenerating.collect { isGenerating ->
                binding.layoutTyping.visibility = if (isGenerating) View.VISIBLE else View.GONE
                binding.btnSend.isEnabled = !isGenerating
                binding.btnAttach.isEnabled = !isGenerating
            }
        }

        // Attachment button opens the attach / generate menu (Issue 4).
        binding.btnAttach.setOnClickListener { openAttachMenu() }

        binding.btnSend.setOnClickListener { onSendClicked() }

        // Compact inline voice (mic) button → speech-to-text into the input box.
        binding.btnVoice.setOnClickListener { onVoiceClicked() }

        binding.btnMenu.setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }
        binding.btnNotifications.setOnClickListener {
            Toast.makeText(requireContext(), "🔔 Notifications clicked!", Toast.LENGTH_SHORT).show()
        }
        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }
    }

    /** Reflects the active provider/model in the persistent chip + input hint. */
    private fun updateProviderChip() {
        updateTabSelection()
        val isFree = viewModel.isFreeMode.value
        val isImage = viewModel.isImageMode.value
        when {
            isImage -> {
                binding.chipActiveProvider.text = "🤗 Hugging Face · Image"
                binding.etMessageInput.setHint("Describe an image to generate…")
            }
            isFree -> {
                val fm = viewModel.selectedFreeModel.value
                binding.chipActiveProvider.text = "${fm.logoEmoji} ${fm.displayName}"
                binding.etMessageInput.setHint("Ask ${fm.displayName}…")
            }
            else -> {
                val p = viewModel.effectiveProvider.value
                val label = if (viewModel.webSearchMode.value) "🌐 ${p.displayName}" else "${p.logoEmoji} ${p.displayName}"
                binding.chipActiveProvider.text = label
                binding.etMessageInput.setHint("Ask ${p.displayName}…")
            }
        }
    }

    /** Highlights the active chat-mode tab (Premium / Free / Image). */
    private fun updateTabSelection() {
        val selectedBg = R.drawable.bg_pill_chip_selected
        val transparent = android.R.color.transparent
        val activeText = R.color.white
        val idleText = R.color.outline

        val isFree = viewModel.isFreeMode.value
        val isImage = viewModel.isImageMode.value

        binding.btnTabPremium.setBackgroundResource(if (!isFree && !isImage) selectedBg else transparent)
        binding.btnTabPremium.setTextColor(resources.getColor(if (!isFree && !isImage) activeText else idleText, null))

        binding.btnTabFree.setBackgroundResource(if (isFree) selectedBg else transparent)
        binding.btnTabFree.setTextColor(resources.getColor(if (isFree) activeText else idleText, null))

        binding.btnTabImage.setBackgroundResource(if (isImage) selectedBg else transparent)
        binding.btnTabImage.setTextColor(resources.getColor(if (isImage) activeText else idleText, null))
    }

    private fun onSendClicked() {
        val content = binding.etMessageInput.text?.toString().orEmpty()
        if (content.isBlank() && pendingAttachment == null) return

        if (isGuest && guestMessageCount >= 10) {
            showGuestLimitDialog()
            return
        }

        val att = pendingAttachment
        // For an image on a vision-capable provider, read the bytes and forward them as a
        // base64 data URI so the model can actually see the picture.
        val imageDataUri = if (att?.type == "image") uriToDataUri(att.uri) else null

        viewModel.sendMessage(
            content = content,
            attachmentType = att?.type,
            attachmentUri = att?.uri,
            attachmentName = att?.name,
            imageDataUri = imageDataUri
        )

        if (isGuest) {
            lifecycleScope.launch { preferencesManager.incrementGuestMessageCount() }
        }

        binding.etMessageInput.text?.clear()
        clearAttachment()
    }

    /* ─────────────────────────────────────────────────────────────────────────
     * Inline voice input (compact mic button)
     * ───────────────────────────────────────────────────────────────────────── */

    private fun onVoiceClicked() {
        if (isVoiceListening) {
            voiceHelper?.stopListening()
            return
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceInput()
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceInput() {
        if (voiceHelper == null) {
            voiceHelper = VoiceRecognitionHelper(requireContext(), object : VoiceRecognitionHelper.Callback {
                override fun onReadyForSpeech() {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onPartialResults(text: String) { appendVoiceText(text) }
                override fun onResults(text: String) {
                    appendVoiceText(text)
                    stopVoiceVisual()
                }
                override fun onError(error: String) {
                    stopVoiceVisual()
                    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                }
            })
        }
        voiceHelper?.startListening()
        isVoiceListening = true
        binding.btnVoice.setColorFilter(
            ContextCompat.getColor(requireContext(), R.color.electric_blue_glow),
            PorterDuff.Mode.SRC_IN
        )
        binding.btnVoice.alpha = 0.7f
    }

    private fun stopVoiceVisual() {
        isVoiceListening = false
        binding.btnVoice.setColorFilter(
            ContextCompat.getColor(requireContext(), R.color.html_cyan),
            PorterDuff.Mode.SRC_IN
        )
        binding.btnVoice.alpha = 1f
    }

    /** Inserts recognised speech into the message box without destroying what's already there. */
    private fun appendVoiceText(text: String) {
        if (text.isBlank()) return
        val current = binding.etMessageInput.text?.toString().orEmpty()
        val merged = if (current.isBlank()) text else "$current $text".trim()
        binding.etMessageInput.setText(merged)
        binding.etMessageInput.setSelection(binding.etMessageInput.text?.length ?: 0)
    }

    /* ─────────────────────────────────────────────────────────────────────────
     * File attachment flow
     * ───────────────────────────────────────────────────────────────────────── */

    private fun openAttachMenu() {
        val context = requireContext()
        val dialog = BottomSheetDialog(context)
        val scroll = ScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
        }

        val isFree = viewModel.isFreeMode.value
        val options = listOf(
            "📷 Photo" to "image/*",
            "🎥 Video" to "video/*",
            "📄 PDF" to "application/pdf",
            "🎵 Audio" to "audio/*"
        )

        options.forEach { (label, mime) ->
            layout.addView(menuButton(label) {
                dialog.dismiss()
                pickMedia.launch(mime)
            })
        }

        // Image generation only works from the key-less Free AI tab (Pollinations).
        layout.addView(menuButton("🖼 Generate Image (Free AI)") {
            dialog.dismiss()
            if (isFree) {
                val prompt = binding.etMessageInput.text?.toString().orEmpty()
                if (prompt.isBlank()) {
                    Toast.makeText(context, "Type a description first, then tap Generate Image.", Toast.LENGTH_SHORT).show()
                    return@menuButton
                }
                viewModel.generateImage(prompt)
                binding.etMessageInput.text?.clear()
            } else {
                Toast.makeText(
                    context,
                    "Image generation lives in the 🖼 Image tab (Hugging Face). The selected chat provider is text-only.",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        scroll.addView(layout)
        dialog.setContentView(scroll)
        dialog.show()
    }

    private fun menuButton(label: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(requireContext()).apply {
            text = label
            setTextColor(resources.getColor(R.color.white, null))
            setBackgroundColor(resources.getColor(R.color.glass_surface, null))
            cornerRadius = 24
            setPadding(24, 20, 24, 20)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun handlePicked(uri: Uri) {
        val cr = requireContext().contentResolver
        val mime = cr.getType(uri) ?: "application/octet-stream"
        val type = when {
            mime.startsWith("image/") -> "image"
            mime.startsWith("video/") -> "video"
            mime.startsWith("audio/") -> "audio"
            mime == "application/pdf" -> "pdf"
            else -> "file"
        }
        val name = runCatching {
            cr.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull() ?: "attachment"

        pendingAttachment = PendingAttachment(type, uri.toString(), name, mime)
        showAttachmentPreview()
    }

    private fun showAttachmentPreview() {
        val att = pendingAttachment ?: return
        binding.containerAttachmentPreview.removeAllViews()

        val emoji = when (att.type) {
            "image" -> "🖼"
            "video" -> "🎥"
            "audio" -> "🎵"
            "pdf" -> "📄"
            else -> "📎"
        }

        val chip = Chip(requireContext()).apply {
            text = "$emoji ${att.name}"
            isCloseIconVisible = true
            setOnCloseIconClickListener { clearAttachment() }
            chipBackgroundColor = ColorStateList.valueOf(
                resources.getColor(R.color.glass_background, null)
            )
            setTextColor(resources.getColor(R.color.white, null))
            chipStrokeColor = ColorStateList.valueOf(
                resources.getColor(R.color.glass_stroke, null)
            )
            chipStrokeWidth = 1f
        }
        binding.containerAttachmentPreview.addView(chip)
        binding.layoutAttachmentPreview.visibility = View.VISIBLE
    }

    private fun clearAttachment() {
        pendingAttachment = null
        binding.containerAttachmentPreview.removeAllViews()
        binding.layoutAttachmentPreview.visibility = View.GONE
    }

    /** Reads a content URI and returns a `data:image/…;base64,…` payload for vision requests. */
    private fun uriToDataUri(uriString: String): String? = runCatching {
        val uri = Uri.parse(uriString)
        val cr = requireContext().contentResolver
        val mime = cr.getType(uri) ?: "image/*"
        val fmt = mime.substringAfter("/").replace("jpeg", "jpg").replace("+xml", "")
        cr.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            "data:image/$fmt;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        }
    }.getOrNull()

    private fun setupFreeModelChips() {
        val chipGroup = binding.chipGroupFreeModel
        chipGroup.removeAllViews()

        viewModel.freeModels.forEach { model ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = "${model.logoEmoji} ${model.displayName}"
                tag = model.id
                isCheckable = true
                isCheckedIconVisible = true
                setTextColor(resources.getColor(R.color.white, null))
                chipBackgroundColor = ColorStateList.valueOf(
                    resources.getColor(R.color.glass_background, null)
                )
                chipStrokeWidth = 1f
                chipStrokeColor = ColorStateList.valueOf(
                    resources.getColor(R.color.glass_stroke, null)
                )
                setOnClickListener { viewModel.selectFreeModel(model.id) }
            }
            chipGroup.addView(chip)
        }

        lifecycleScope.launch {
            viewModel.selectedFreeModel.collect { selected ->
                chipGroup.children.filterIsInstance<Chip>().forEach { chip ->
                    val isSelected = chip.tag == selected.id
                    if (chip.isChecked != isSelected) chip.isChecked = isSelected
                    chip.chipStrokeColor = ColorStateList.valueOf(
                        resources.getColor(
                            if (isSelected) R.color.electric_blue_glow else R.color.glass_stroke,
                            null
                        )
                    )
                }
            }
        }
    }

    private fun showGuestLimitDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Guest limit reached")
            .setMessage("You've used all 10 free messages. Sign in with Google to continue using PK AI.")
            .setPositiveButton("Sign in with Google") { _, _ ->
                findNavController().navigate(R.id.loginFragment)
            }
            .setNegativeButton("Maybe later", null)
            .show()
    }

    override fun onDestroyView() {
        voiceHelper?.destroy()
        voiceHelper = null
        super.onDestroyView()
        _binding = null
    }
}
