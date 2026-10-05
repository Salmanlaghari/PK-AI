package com.salmanlaghari.pkai.ui.tips

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tips screen — helpful usage tips plus the hidden 🔞 Super Stickers unlock.
 *
 * Shown as a bottom sheet: reachable from the Super Chat header button and
 * auto-popped on first chat entry (until the user opts out via the
 * "Don't show automatically again" checkbox, persisted in PreferencesManager).
 *
 * The "Secret Stickers" card is gated behind an 18+ confirm dialog; confirming
 * reveals the redeem code which unlocks the Super Stickers pack in the sticker
 * picker (persisted in SharedPreferences).
 */
@AndroidEntryPoint
class TipsFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_tips, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("super_chat_prefs", Context.MODE_PRIVATE)

        // Open fully expanded — the tips read like a sheet, swipe down to dismiss.
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        // Start the live water background animation 🌊
        (view.findViewById<View>(R.id.tipsRoot).background as? android.graphics.drawable.AnimationDrawable)?.start()

        view.findViewById<View>(R.id.btnTipsBack).setOnClickListener {
            findNavController().navigateUp()
        }

        // "Don't show automatically again" — opts out of the auto-popup.
        val dontShowCheck = view.findViewById<CheckBox>(R.id.cb_tips_dont_show_again)
        viewLifecycleOwner.lifecycleScope.launch {
            dontShowCheck.isChecked = runCatching {
                preferencesManager.tipsDontShowAgain.first()
            }.getOrDefault(false)
        }
        dontShowCheck.setOnCheckedChangeListener { _, checked ->
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { preferencesManager.setTipsDontShowAgain(checked) }
            }
        }

        val recycler = view.findViewById<RecyclerView>(R.id.rvTips)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = TipsAdapter(buildTips()) { tip ->
            if (tip.isSecret) onSecretTipClicked()
        }
    }

    private fun buildTips(): List<TipItem> {
        val unlocked = prefs.getBoolean(KEY_SUPER_UNLOCKED, false)
        return listOf(
            TipItem("💬", "Smart Chat", "Apne alfaaz mein baat karein — PK AI aapke mood ke hisaab se jawab aur pose badalta hai."),
            TipItem("🎨", "Stickers", "Har jawab ke saath mood sticker aata hai. 💜 dabakar favorite banayein."),
            TipItem("🎤", "Voice Notes", "Mic dabayein, boliye, dobara dabayein — voice note chat mein aa jayega."),
            TipItem("📎", "Images", "📎 se gallery se tasveer bhejein aur us par baat karein."),
            TipItem("🔊", "Suniye", "Kisi bhi jawab par 🔊 dabakar use sun sakte hain."),
            TipItem("⚡", "Super Stickers Shortcut", "Super Chat mein /18+ likh kar bhejein — special sticker mode foran on ho jayega!"),
            TipItem(
                "🔞",
                "Secret Stickers",
                if (unlocked) "✅ Super Stickers UNLOCKED — sticker picker mein ⚡ SUPER section dekhein!"
                else "Ek khufiya code hai jo Super Stickers on karta hai…",
                isSecret = true
            )
        )
    }

    private fun onSecretTipClicked() {
        if (prefs.getBoolean(KEY_SUPER_UNLOCKED, false)) {
            Toast.makeText(requireContext(), "✅ Super Stickers already unlocked!", Toast.LENGTH_SHORT).show()
            return
        }
        // Step 1: 18+ age gate
        AlertDialog.Builder(requireContext())
            .setTitle("🔞 18+ Confirm")
            .setMessage("Ye section sirf 18 saal ya us se zyada umar walon ke liye hai.\n\nKya aap 18+ hain?")
            .setPositiveButton("Haan, 18+ hoon") { _, _ -> showRedeemDialog() }
            .setNegativeButton("Nahi") { d, _ -> d.dismiss() }
            .show()
    }

    private fun showRedeemDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Code likhein…"
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("⚡ Super Stickers Unlock")
            .setMessage("🔞 Is 18+ section ka access code enter karein.\n\nHint: $SUPER_CODE_HINT")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                val code = input.text.toString().trim()
                if (sha256(code) == SUPER_CODE_SHA256) {
                    prefs.edit().putBoolean(KEY_SUPER_UNLOCKED, true).apply()
                    Toast.makeText(
                        requireContext(),
                        "🎉 Super Stickers ON! Sticker picker khol kar dekhein!",
                        Toast.LENGTH_LONG
                    ).show()
                    // Refresh the list to show unlocked state
                    view?.findViewById<RecyclerView>(R.id.rvTips)?.adapter =
                        TipsAdapter(buildTips()) { tip -> if (tip.isSecret) onSecretTipClicked() }
                } else {
                    Toast.makeText(requireContext(), "❌ Ghalat code!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    data class TipItem(
        val emoji: String,
        val title: String,
        val body: String,
        val isSecret: Boolean = false
    )

    private class TipsAdapter(
        private val tips: List<TipItem>,
        private val onClick: (TipItem) -> Unit
    ) : RecyclerView.Adapter<TipsAdapter.TipHolder>() {

        class TipHolder(view: View) : RecyclerView.ViewHolder(view) {
            val emoji: TextView = view.findViewById(R.id.tvTipEmoji)
            val title: TextView = view.findViewById(R.id.tvTipTitle)
            val body: TextView = view.findViewById(R.id.tvTipBody)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TipHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tip, parent, false)
            return TipHolder(v)
        }

        override fun getItemCount(): Int = tips.size

        override fun onBindViewHolder(holder: TipHolder, position: Int) {
            val tip = tips[position]
            holder.emoji.text = tip.emoji
            holder.title.text = tip.title
            holder.body.text = tip.body
            holder.itemView.setOnClickListener { onClick(tip) }
            // Secret card gets the premium 3D card treatment
            holder.itemView.background = holder.itemView.context.getDrawable(
                if (tip.isSecret) R.drawable.bg_ai_3d_card
                else R.drawable.bg_superchat_ai_bubble
            )
        }
    }

    companion object {
        const val KEY_SUPER_UNLOCKED = "super_stickers_unlocked"
        private const val SUPER_CODE_HINT = "PKAI-SUPER-18"
        internal const val SUPER_CODE_SHA256 = "c3f10ca04036997bb2fabce7227a59129fc58ba49c51ba21df3183d1b69d67a7"
        internal fun sha256(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.trim().uppercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
