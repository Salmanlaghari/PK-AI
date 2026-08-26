package com.salmanlaghari.pkai.ui.superchat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.model.ChatMessage
import com.salmanlaghari.pkai.databinding.FragmentSuperChatBinding
import com.salmanlaghari.pkai.util.SpriteSheetLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Super Chat — a mood-reactive avatar companion session.
 *
 * Every user message is analysed for emotion; the avatar instantly switches to a
 * matching pose with a 300ms crossfade. Replies stream from the user's default AI
 * provider with a warm offline fallback. Pose sheets are loaded from
 * `assets/poses/` when present; otherwise built-in placeholder poses are used.
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
    private var hqRendering = true

    /** Contents of AI messages the user hearted, for the 💖 badge. */
    private val favoritedContents = mutableSetOf<String>()

    /** Pending sticker swap while the fade-out half of the crossfade runs. */
    private var pendingSticker: Int? = null

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

        setupChat()
        setupSidePanel()
        setupHeader()
        initTts()
        observeViewModel()
        showSticker(viewModel.currentSticker.value, animate = false)
    }

    private fun setupChat() {
        adapter = SuperChatAdapter(
            onSpeak = { speak(it) },
            onCopy = { copy(it) },
            onFavorite = { toggleMessageFavorite(it) },
            onShare = { share(it) }
        )
        binding.rvSuperChat.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSuperChat.adapter = adapter
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.navBackToChat.setOnClickListener { findNavController().popBackStack() }
        binding.btnSuperSend.setOnClickListener { onSendClicked() }
        binding.etSuperChatInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                onSendClicked(); true
            } else false
        }
    }

    private fun onSendClicked() {
        val text = binding.etSuperChatInput.text?.toString().orEmpty()
        if (text.isBlank()) return
        binding.etSuperChatInput.setText("")
        viewModel.sendMessage(text)
    }

    private fun setupSidePanel() {
        binding.btnStickers.setOnClickListener { showStickerPicker(favoritesOnly = false) }
        binding.btnFavorites.setOnClickListener { showStickerPicker(favoritesOnly = true) }
        binding.btnSaved.setOnClickListener { showStickerPicker(favoritesOnly = false) }
        binding.btnMoreStickers.setOnClickListener { showStickerPicker(favoritesOnly = false) }
        binding.btnLivePose.setOnClickListener {
            val newState = !viewModel.livePoseEnabled.value
            viewModel.setLivePoseEnabled(newState)
            toast(
                if (newState) getString(R.string.superchat_live_pose_on)
                else getString(R.string.superchat_live_pose_off)
            )
        }
        binding.btnHq.setOnClickListener {
            hqRendering = !hqRendering
            toast(
                if (hqRendering) getString(R.string.superchat_hq_on)
                else getString(R.string.superchat_hq_off)
            )
        }
        setupPoseThumbnails()
    }

    /** Fills the 2×3 pose grid with a spread of stickers; tap swaps the avatar. */
    private fun setupPoseThumbnails() {
        val available = SpriteSheetLoader.availableStickers(requireContext())
        val thumbs = listOf(
            binding.poseThumb1, binding.poseThumb2, binding.poseThumb3,
            binding.poseThumb4, binding.poseThumb5, binding.poseThumb6
        )
        thumbs.forEachIndexed { i, thumb ->
            if (available.isEmpty()) return@forEachIndexed
            val index = available[(i * available.size / thumbs.size.coerceAtLeast(1)) % available.size]
            thumb.setImageBitmap(SpriteSheetLoader.getSticker(requireContext(), index))
            thumb.setOnClickListener {
                viewModel.selectSticker(index)
                showSticker(index, animate = true)
            }
        }
    }

    private fun showStickerPicker(favoritesOnly: Boolean) {
        if (childFragmentManager.findFragmentByTag("sticker_picker") != null) return
        val favorites = viewModel.favorites.value
        val picker = StickerPickerDialogFragment.newInstance(
            indices = PoseRegistryIndices.forPicker(requireContext(), favorites, favoritesOnly),
            favorites = favorites,
            onPick = { index ->
                viewModel.selectSticker(index)
                showSticker(index, animate = true)
            },
            onToggleFavorite = { index ->
                viewModel.toggleFavorite(index)
            }
        )
        picker.show(childFragmentManager, "sticker_picker")
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messages ->
                        adapter.submitList(messages) {
                            binding.rvSuperChat.scrollToPosition((messages.size - 1).coerceAtLeast(0))
                        }
                    }
                }
                launch {
                    viewModel.currentSticker.collect { index ->
                        showSticker(index, animate = true)
                    }
                }
                launch {
                    viewModel.currentMood.collect { mood ->
                        binding.tvPoseLabel.text = "${mood.emoji} ${mood.label}"
                    }
                }
                launch {
                    viewModel.isGenerating.collect { generating ->
                        binding.btnSuperSend.isEnabled = !generating
                    }
                }
                launch {
                    viewModel.livePoseEnabled.collect { enabled ->
                        if (enabled) startLivePose() else stopLivePose()
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

    /** Swaps the avatar sticker with a 300ms crossfade, then starts its 4D/5D motion. */
    private fun showSticker(index: Int, animate: Boolean) {
        val imageView = binding.ivSticker
        if (!animate || pendingSticker == index) {
            pendingSticker = null
            imageView.setImageBitmap(SpriteSheetLoader.getSticker(requireContext(), index))
            imageView.alpha = 1f
            startPoseMotion(index)
            return
        }
        if (pendingSticker == index) return
        pendingSticker = index

        imageView.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                if (!isAdded) return@withEndAction
                imageView.setImageBitmap(SpriteSheetLoader.getSticker(requireContext(), index))
                imageView.animate().alpha(1f).setDuration(150).withEndAction {
                    pendingSticker = null
                    startPoseMotion(index)
                }.start()
            }
            .start()
    }

    /** Runs the pose-matched looping motion (shake / bounce / pulse / tilt / sway). */
    private fun startPoseMotion(index: Int) {
        if (_binding == null) return
        StickerMotion.start(binding.ivSticker, StickerMotion.styleFor(index))
    }

    /** Gentle breathing/sway loop on the avatar for the "Live Pose" feel. */
    private fun startLivePose() {
        val ringPulse = AlphaAnimation(0.5f, 1f).apply {
            duration = 1200; repeatMode = Animation.REVERSE; repeatCount = Animation.INFINITE
        }
        binding.glowRing.startAnimation(ringPulse)
    }

    private fun stopLivePose() {
        binding.glowRing.clearAnimation()
        StickerMotion.stop(binding.ivSticker)
    }

    /* ── Message actions ─────────────────────────────────────────────────── */

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
        binding.glowRing.clearAnimation()
        StickerMotion.stop(binding.ivSticker)
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
