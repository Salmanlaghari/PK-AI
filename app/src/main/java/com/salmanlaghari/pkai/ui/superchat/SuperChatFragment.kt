package com.salmanlaghari.pkai.ui.superchat

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import com.salmanlaghari.pkai.databinding.FragmentSuperChatBinding
import com.salmanlaghari.pkai.ui.chat.ChatAutoScroller
import com.salmanlaghari.pkai.ui.tips.TipsAutoPopup
import com.salmanlaghari.pkai.util.TtsHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

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

    @Inject
    lateinit var preferencesManager: PreferencesManager

    /** Shared per-message actions (speak/copy/favorite/share/fullscreen) — also used by Home. */
    private lateinit var messageActions: ChatMessageActions
    /** Shared smart auto-scroll — also used by Home. */
    private val chatAutoScroller = ChatAutoScroller()

    // ── Voice recording ──────────────────────────────────────────────
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStartMs: Long = 0L

    // Song streaming player 🎵
    private var songPlayer: MediaPlayer? = null
    private var playingSongId: String? = null
    private var isSongPaused = false
    private var recordingJob: Job? = null
    private var isRecording = false

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { }
                viewLifecycleOwner.lifecycleScope.launch {
                    val imageDataUri = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        uriToImageDataUri(uri)
                    }
                    if (imageDataUri == null) {
                        toast("Image read nahi hui — 4 MB se chhoti image select karein")
                    } else {
                        viewModel.sendImageMessage(uri.toString(), imageDataUri)
                    }
                }
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
        messageActions = ChatMessageActions(requireContext())

        // Start the live water background animation 🌊
        (binding.superChatRoot.background as? android.graphics.drawable.AnimationDrawable)?.start()

        setupChat()
        val appContext = context?.applicationContext
        if (appContext != null) {
            viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                pruneVoiceNotes(appContext)
            }
        }
        setupHeader()
        setupMediaButtons()
        setupBannerAd()
        observeViewModel()

        // Tips auto-popup on first chat entry (skipped when opted out).
        TipsAutoPopup.maybeShow(this, preferencesManager, "superchat")
    }

    private fun setupChat() {
        adapter = SuperChatAdapter(
            onSpeak = { messageActions.speak(it) },
            onCopy = { messageActions.copy(it) },
            onFavorite = {
                messageActions.toggleFavorite(it)
                adapter.favoriteContents = messageActions.favoriteContents
                adapter.notifyDataSetChanged()
            },
            onShare = { messageActions.share(it) },
            onImageClick = { messageActions.showFullscreenImage(it) }
        )
        adapter.onSongPlayClicked = { toggleSongPlayback(it) }
        binding.rvSuperChat.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSuperChat.adapter = adapter
    }

    /** Toggles streaming playback for a song card. */
    private fun toggleSongPlayback(message: com.salmanlaghari.pkai.data.model.ChatMessage) {
        val audioUrl = message.attachmentUri
        if (audioUrl.isNullOrBlank()) {
            Toast.makeText(requireContext(), "😔 Audio stream nahi mila", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            // Tapping the currently playing song toggles pause/resume (id stays set)
            if (playingSongId == message.id && songPlayer != null) {
                val player = songPlayer!!
                if (player.isPlaying) {
                    player.pause()
                    isSongPaused = true
                } else {
                    player.start()
                    isSongPaused = false
                }
                adapter.playingSongId = message.id
                adapter.isSongPaused = isSongPaused
                adapter.notifyDataSetChanged()
                return
            }
            // Stop any previous song
            try { songPlayer?.stop() } catch (_: Exception) { }
            songPlayer?.release()
            songPlayer = null
            isSongPaused = false
            adapter.isSongPaused = false
            Toast.makeText(requireContext(), "🎵 Loading song…", Toast.LENGTH_SHORT).show()
            // Build in a local first: if setDataSource throws, the instance is
            // still reachable here and gets released (no native leak).
            val newPlayer = MediaPlayer()
            try {
                newPlayer.setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                newPlayer.setDataSource(audioUrl)
                // Assign BEFORE prepareAsync so callbacks always see the field
                songPlayer = newPlayer
                newPlayer.setOnPreparedListener {
                    it.start()
                    playingSongId = message.id
                    isSongPaused = false
                    adapter.playingSongId = message.id
                    adapter.isSongPaused = false
                    adapter.notifyDataSetChanged()
                }
                newPlayer.setOnCompletionListener {
                    playingSongId = null
                    isSongPaused = false
                    adapter.playingSongId = null
                    adapter.isSongPaused = false
                    adapter.notifyDataSetChanged()
                }
                newPlayer.setOnErrorListener { _, _, _ ->
                    Toast.makeText(requireContext(), "😔 Song play nahi ho saka", Toast.LENGTH_SHORT).show()
                    playingSongId = null
                    isSongPaused = false
                    adapter.playingSongId = null
                    adapter.isSongPaused = false
                    adapter.notifyDataSetChanged()
                    true
                }
                newPlayer.prepareAsync()
            } catch (e: Exception) {
                android.util.Log.w("SuperChat", "Song setDataSource failed: ${e.message}")
                try { newPlayer.release() } catch (_: Exception) { }
                if (songPlayer === newPlayer) songPlayer = null
                throw e
            }
        } catch (e: Exception) {
            android.util.Log.w("SuperChat", "Song playback failed: ${e.message}")
            Toast.makeText(requireContext(), "😔 Song play nahi ho saka", Toast.LENGTH_SHORT).show()
            try { songPlayer?.release() } catch (_: Exception) { }
            songPlayer = null
            playingSongId = null
            isSongPaused = false
            adapter.playingSongId = null
            adapter.isSongPaused = false
            adapter.notifyDataSetChanged()
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop voice recording if the user leaves mid-recording
        if (isRecording) stopVoiceRecording(send = false)
        // Pause song playback when leaving the screen — mirror the paused
        // state so the card icon doesn't lie
        try {
            songPlayer?.takeIf { it.isPlaying }?.let {
                it.pause()
                isSongPaused = true
                adapter.isSongPaused = true
                adapter.notifyDataSetChanged()
            }
        } catch (_: Exception) { }
        // Stop wallpaper animation off-screen (battery)
        (view?.findViewById<View>(R.id.superChatRoot)?.background as? android.graphics.drawable.AnimationDrawable)?.stop()
    }

    override fun onResume() {
        super.onResume()
        (view?.findViewById<View>(R.id.superChatRoot)?.background as? android.graphics.drawable.AnimationDrawable)?.start()
    }

    /** Loads the banner ad — this was the missing piece (ads never showed). */
    private fun setupBannerAd() {
        try {
            val adView = com.salmanlaghari.pkai.ads.AdManager.createBannerAdView(
                requireContext(),
                com.salmanlaghari.pkai.ads.AdManager.BANNER_HOME_ID
            )
            binding.bannerAdContainer.addView(adView)
        } catch (e: Exception) {
            android.util.Log.w("SuperChat", "Banner ad failed: ${e.message}")
        }
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.navBackToChat.setOnClickListener { findNavController().popBackStack() }
        binding.btnTips.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            findNavController().navigate(R.id.action_superChatFragment_to_tipsFragment)
        }
        binding.btnHistory.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            findNavController().navigate(R.id.action_superChatFragment_to_historyFragment)
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
            pickImageLauncher.launch(arrayOf("image/*"))
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
                    kotlinx.coroutines.flow.combine(viewModel.messages, viewModel.isGenerating) { messages, generating ->
                        messages to generating
                    }.collect { (messages, generating) ->
                        binding.btnSuperSend.isEnabled = !generating
                        val items = messages.map { SuperChatAdapter.Item.Message(it) } +
                            (if (generating) listOf(SuperChatAdapter.Item.Typing) else emptyList())
                        val rv = binding.rvSuperChat
                        val shouldScroll = chatAutoScroller.shouldScrollToBottom(
                            rv,
                            adapter.itemCount,
                            items.size,
                            messages.size,
                            messages.lastOrNull()?.isUser == true
                        )
                        adapter.submitList(items) {
                            if (shouldScroll) {
                                rv.smoothScrollToPosition(items.size - 1)
                            }
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

    /* ── Voice recording ────────────────────────────────────────────── */

    private fun startVoiceRecording() {
        if (isRecording) return
        try {
            val dir = File(requireContext().filesDir, "voice_notes").apply { mkdirs() }
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

    private fun pruneVoiceNotes(appContext: Context) {
        val dir = File(appContext.filesDir, "voice_notes")
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        files.filter { it.lastModified() < cutoff }.forEach { it.delete() }
        var total = files.filter { it.exists() }.sumOf { it.length() }
        val maxBytes = 10L * 1024 * 1024
        files.filter { it.exists() }.sortedBy { it.lastModified() }.forEach { file ->
            if (recordingFile?.absolutePath == file.absolutePath) return@forEach
            val size = file.length()
            if (total > maxBytes && file.delete()) total -= size
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
        recordingFile = null
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

    /** Reads a bounded image into a vision-compatible data URI. */
    private fun uriToImageDataUri(uri: android.net.Uri): String? = runCatching {
        val resolver = requireContext().contentResolver
        val mime = resolver.getType(uri)?.takeIf { it.startsWith("image/") }
            ?: return@runCatching null
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > 4 * 1024 * 1024) return@runCatching null
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: return@runCatching null
        "data:$mime;base64,${android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)}"
    }.getOrNull()

    /* ── Favorites persistence (sticker indices) ──────────────────── */

    private fun loadFavorites(): Set<Int> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())
            ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

    private fun persistFavorites(favorites: Set<Int>) {
        prefs.edit()
            .putStringSet(KEY_FAVORITES, favorites.map { it.toString() }.toSet())
            .apply()
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        if (isRecording) stopVoiceRecording(send = false)
        adapter.releasePlayer()
        // Stop TTS speech so audio never keeps playing after leaving the screen
        TtsHelper.stop()
        // Release the song streaming player so audio never leaks past the screen
        try { songPlayer?.stop() } catch (_: Exception) { }
        try { songPlayer?.release() } catch (_: Exception) { }
        songPlayer = null
        playingSongId = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val KEY_FAVORITES = "favorite_stickers"
    }
}
