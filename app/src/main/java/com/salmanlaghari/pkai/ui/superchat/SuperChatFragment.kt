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
    /** Contents of AI messages the user hearted, for the 💖 badge. */
    private val favoritedContents = mutableSetOf<String>()

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
        setupHeader()
        initTts()
        observeViewModel()
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
                    viewModel.messageStickers.collect { map ->
                        adapter.setStickers(map)
                    }
                }
                launch {
                    viewModel.isGenerating.collect { generating ->
                        binding.btnSuperSend.isEnabled = !generating
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
