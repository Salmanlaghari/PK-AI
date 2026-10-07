package com.salmanlaghari.pkai.ui.tips

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
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
 * Tips screen — helpful usage tips for Super Chat.
 *
 * Shown as a bottom sheet: reachable from the Super Chat header button and
 * auto-popped on first chat entry (until the user opts out via the
 * "Don't show automatically again" checkbox, persisted in PreferencesManager).
 */
@AndroidEntryPoint
class TipsFragment : BottomSheetDialogFragment() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_tips, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
        recycler.adapter = TipsAdapter(buildTips())
    }

    private fun buildTips(): List<TipItem> {
        return listOf(
            TipItem("💬", "Smart Chat", "Apne alfaaz mein baat karein — PK AI aapke mood ke hisaab se jawab aur pose badalta hai."),
            TipItem("🎨", "Stickers", "Har jawab ke saath mood sticker aata hai. 💜 dabakar favorite banayein."),
            TipItem("🎤", "Voice Notes", "Mic dabayein, boliye, dobara dabayein — voice note chat mein aa jayega."),
            TipItem("📎", "Images", "📎 se gallery se tasveer bhejein aur us par baat karein."),
            TipItem("🔊", "Suniye", "Kisi bhi jawab par 🔊 dabakar use sun sakte hain.")
        )
    }

    data class TipItem(
        val emoji: String,
        val title: String,
        val body: String
    )

    private class TipsAdapter(
        private val tips: List<TipItem>
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
            holder.itemView.background = holder.itemView.context.getDrawable(
                R.drawable.bg_superchat_ai_bubble
            )
        }
    }
}
