package com.salmanlaghari.pkai.ui.superchat

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.model.ChatMessage
import com.salmanlaghari.pkai.databinding.FragmentSuperChatBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Super Chat — a mood-reactive avatar companion session with a professional,
 * animated chat experience.
 *
 * - Messages animate in (user slides from right, AI 3D card flips in).
 * - Animated typing indicator while the AI generates.
 * - Smart auto-scroll: follows new messages only when already near bottom.
 * - Multimedia: image attachments + voice notes.
 */
@AndroidEntryPoint
class SuperChatFragment : Fragment() {

    private var _binding: FragmentSuperChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SuperChatViewModel by viewModels()
    private lateinit var adapter: SuperChatAdapter
    private lateinit var prefs: SharedPreferences

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    /** Contents of AI messages the user hearted, for the 💖 badge. */
    private val favoritedContents = mutableSetOf<String>()

    // ── Voice recording ──────────────────────────────────────────────
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartMs: Long = 0L
    private var recordingJob: Job? = null
    private var isRecording = false

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { }
                viewModel.sendImageMessage(uri.toString())
            }
        }

    private val requestAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startVoiceRecording()
            else toast("🎤 ke liye microphone permission chahiye")
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSuperChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext()
            .getSharedPreferences("super_chat_prefs", Context.MODE_PRIVATE)
        viewModel.setFavorites(loadFavorites())

        // Start the live water background animation 🌊
        (binding.superChatRoot.background as? android.graphics.drawable.AnimationDrawable)?.start()

        setupChat()
        setupHeader()
        setupMediaButtons()
        initTts()
        observeViewModel()
    }

    private fun setupChat() {
        adapter = SuperChatAdapter(
            onSpeak = { speak(it) },
            onCopy = { copy(it) },
            onFavorite = { toggleMessageFavorite(it) },
            onShare = { share(it) },
            onImageClick = { showFullscreenImage(it) }
        )
        binding.rvSuperChat.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSuperChat.adapter = adapter
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.navBackToChat.setOnClickListener { findNavController().popBackStack() }
        binding.btnTips.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            findNavController().navigate(R.id.action_superChatFragment_to_tipsFragment)
        }
        binding.btnSuperSend.setOnClickListener {
            popSendButton()
            onSendClicked()
        }
        binding.chipPkAi.setOnClickListener {
            viewModel.togglePkAiMode()
            val state = if (viewModel.isPkAiMode.value) "PK AI Calendar Assistant Active" else "PK AI Mode Paused"
            Toast.makeText(requireContext(), state, Toast.LENGTH_SHORT).show()
        }
        binding.etSuperChatInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                popSendButton()
                onSendClicked(); true
            } else false
        }
    }

    private fun setupMediaButtons() {
        binding.btnAttach.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            pickImageLauncher.launch("image/*")
        }
        binding.btnVoice.setOnClickListener {
            if (isRecording) stopVoiceRecording(send = true)
            else checkAudioPermissionAndRecord()
        }
        binding.btnVoice.setOnLongClickListener {
            if (!isRecording) checkAudioPermissionAndRecord()
            true
        }
    }

    private fun checkAudioPermissionAndRecord() {
        val perm = android.Manifest.permission.RECORD_AUDIO
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(), perm
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceRecording()
        } else {
            requestAudioPermissionLauncher.launch(perm)
        }
    }

    /** Scale-pop + haptic on the send button. */
    private fun popSendButton() {
        binding.btnSuperSend.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        binding.btnSuperSend.animate()
            .scaleX(0.82f).scaleY(0.82f)
            .setDuration(90)
            .withEndAction {
                binding.btnSuperSend.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(140)
                    .start()
            }
            .start()
    }

    private fun onSendClicked() {
        val text = binding.etSuperChatInput.text?.toString().orEmpty()
        if (text.isBlank()) return
        binding.etSuperChatInput.setText("")
        viewModel.sendMessage(text)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messages ->
                        val items = messages.map { SuperChatAdapter.Item.Message(it) } +
                            (if (viewModel.isGenerating.value) listOf(SuperChatAdapter.Item.Typing) else emptyList())
                        val nearBottom = isNearBottom()
                        adapter.submitList(items) {
                            if (nearBottom || messages.isNotEmpty()) {
                                binding.rvSuperChat.smoothScrollToPosition(items.size - 1)
                            }
                        }
                    }
                }
                launch {
                    viewModel.isGenerating.collect { generating ->
                        binding.btnSuperSend.isEnabled = !generating
                        // Rebuild list to show/hide the typing indicator.
                        val messages = viewModel.messages.value
                        val items = messages.map { SuperChatAdapter.Item.Message(it) } +
                            (if (generating) listOf(SuperChatAdapter.Item.Typing) else emptyList())
                        adapter.submitList(items) {
                            if (generating) binding.rvSuperChat.smoothScrollToPosition(items.size - 1)
                        }
                    }
                }
                launch {
                    viewModel.messageStickers.collect { map ->
                        adapter.setStickers(map)
                    }
                }
                launch {
                    viewModel.isPkAiMode.collect { active ->
                        binding.chipPkAi.setBackgroundResource(
                            if (active) R.drawable.bg_pkai_badge_active else R.drawable.bg_pkai_badge_inactive
                        )
                        binding.chipPkAi.text = if (active) "✨ PK AI" else "PK AI Off"
                        binding.etSuperChatInput.hint = if (active) {
                            "Ask PK AI to schedule, remind, or chat…"
                        } else {
                            getString(R.string.superchat_hint)
                        }
                    }
                }
                launch {
                    viewModel.favorites.collect {
                        persistFavorites(it)
                    }
                }
            }
        }
    }

    /** True when the list is scrolled within ~3 items of the bottom. */
    private fun isNearBottom(): Boolean {
        val lm = binding.rvSuperChat.layoutManager as? LinearLayoutManager ?: return true
        val lastVisible = lm.findLastVisibleItemPosition()
        val total = adapter.itemCount
        return total == 0 || lastVisible >= total - 3
    }

    /* ── Voice recording ────────────────────────────────────────────── */

    private fun startVoiceRecording() {
        if (isRecording) return
        try {
            val dir = File(requireContext().cacheDir, "voice_notes").apply { mkdirs() }
            recordingFile = File(dir, "voice_${System.currentTimeMillis()}.m4a")
            recorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(requireContext()) else MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(recordingFile!!.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            recordingStartMs = System.currentTimeMillis()
            binding.btnVoice.text = "⏺"
            vibrateShort()
            toast("🎤 Recording… tap again to send")
            // Safety cap: 60 seconds
            recordingJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(60_000)
                if (isActive && isRecording) stopVoiceRecording(send = true)
            }
        } catch (_: Exception) {
            toast("Microphone unavailable")
            isRecording = false
        }
    }

    private fun stopVoiceRecording(send: Boolean) {
        if (!isRecording) return
        recordingJob?.cancel()
        val secs = ((System.currentTimeMillis() - recordingStartMs) / 1000).coerceAtLeast(1)
        try {
            recorder?.stop()
        } catch (_: Exception) { }
        recorder?.release()
        recorder = null
        isRecording = false
        binding.btnVoice.text = "🎤"
        if (send && recordingFile?.exists() == true && secs >= 1) {
            val label = "${secs / 60}:${String.format("%02d", secs % 60)}"
            viewModel.sendVoiceMessage(
                android.net.Uri.fromFile(recordingFile!!).toString(),
                label
            )
        } else {
            recordingFile?.delete()
        }
    }

    private fun vibrateShort() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                (requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                    .vibrate(60)
            }
        } catch (_: Exception) { }
    }

    /* ── Fullscreen image ───────────────────────────────────────────── */

    private fun showFullscreenImage(uri: String) {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = ImageView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        try {
            requireContext().contentResolver
                .openInputStream(android.net.Uri.parse(uri))?.use { ins ->
                    imageView.setImageBitmap(BitmapFactory.decodeStream(ins))
                }
        } catch (_: Exception) { }
        imageView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(imageView)
        dialog.show()
    }

    /* ── Message actions ────────────────────────────────────────────── */

    private fun speak(message: ChatMessage) {
        if (!ttsReady) {
            toast("Text-to-speech is not ready yet")
            return
        }
        tts?.speak(message.content, TextToSpeech.QUEUE_FLUSH, null, message.id)
    }

    private fun copy(message: ChatMessage) {
        val clipboard = requireContext()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PK AI", message.content))
        toast("Copied to clipboard")
    }

    private fun toggleMessageFavorite(message: ChatMessage) {
        if (!favoritedContents.add(message.content)) {
            favoritedContents.remove(message.content)
        }
        adapter.favoriteContents = favoritedContents
        adapter.notifyDataSetChanged()
    }

    private fun share(message: ChatMessage) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, message.content)
        }
        startActivity(android.content.Intent.createChooser(intent, getString(R.string.superchat_share)))
    }

    private fun loadFavorites(): Set<Int> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

    private fun persistFavorites(favorites: Set<Int>) {
        prefs.edit()
            .putStringSet(KEY_FAVORITES, favorites.map { it.toString() }.toSet())
            .apply()
    }

    private fun initTts() {
        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        if (isRecording) stopVoiceRecording(send = false)
        adapter.releasePlayer()
        tts?.stop()
        tts?.shutdown()
        tts = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val KEY_FAVORITES = "favorite_stickers"
    }
}
