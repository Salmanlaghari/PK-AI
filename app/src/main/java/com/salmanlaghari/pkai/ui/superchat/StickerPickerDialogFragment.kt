package com.salmanlaghari.pkai.ui.superchat

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.util.PoseRegistry
import com.salmanlaghari.pkai.util.SpriteSheetLoader

/**
 * Bottom sheet showing every available avatar sticker in a grid.
 *
 * Tap a sticker to make it the active Super Chat pose; tap the heart badge to
 * favorite/unfavorite it.
 */
class StickerPickerDialogFragment : BottomSheetDialogFragment() {

    companion object {
        fun newInstance(
            indices: IntArray,
            favorites: Set<Int>,
            onPick: (Int) -> Unit,
            onToggleFavorite: (Int) -> Unit
        ): StickerPickerDialogFragment {
            return StickerPickerDialogFragment().also { fragment ->
                fragment.indices = indices
                fragment.favorites = favorites
                fragment.onPick = onPick
                fragment.onToggleFavorite = onToggleFavorite
            }
        }
    }

    private var indices: IntArray = PoseRegistry.allStickers()
    private var favorites: Set<Int> = emptySet()
    private var onPick: ((Int) -> Unit)? = null
    private var onToggleFavorite: ((Int) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_sticker_picker, container, false)
        val recycler = view.findViewById<RecyclerView>(R.id.rvStickerGrid)
        val adapter = StickerGridAdapter(
            indices = indices,
            isFavorite = { favorites.contains(it) },
            onPick = { index ->
                onPick?.invoke(index)
                dismiss()
            },
            onToggleFavorite = { index ->
                onToggleFavorite?.invoke(index)
                favorites = if (favorites.contains(index)) favorites - index else favorites + index
                recycler.adapter?.notifyDataSetChanged()
            }
        )
        recycler.layoutManager = GridLayoutManager(requireContext(), 3)
        recycler.adapter = adapter
        return view
    }

    private class StickerGridAdapter(
        private val indices: IntArray,
        private val isFavorite: (Int) -> Boolean,
        private val onPick: (Int) -> Unit,
        private val onToggleFavorite: (Int) -> Unit
    ) : RecyclerView.Adapter<StickerGridAdapter.StickerViewHolder>() {

        private val bitmapCache = mutableMapOf<Int, Bitmap>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sticker, parent, false)
            return StickerViewHolder(view)
        }

        override fun getItemCount(): Int = indices.size

        override fun onBindViewHolder(holder: StickerViewHolder, position: Int) {
            val index = indices[position]
            val bitmap = bitmapCache.getOrPut(index) {
                SpriteSheetLoader.getSticker(holder.image.context, index)
            }
            holder.image.setImageBitmap(bitmap)
            holder.indexLabel.text = "#${index + 1}"
            holder.favBadge.text = if (isFavorite(index)) "💖" else "🤍"
            holder.image.setOnClickListener { onPick(index) }
            holder.favBadge.setOnClickListener { onToggleFavorite(index) }
        }

        class StickerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val image: ImageView = view.findViewById(R.id.ivStickerCell)
            val favBadge: TextView = view.findViewById(R.id.tvStickerFav)
            val indexLabel: TextView = view.findViewById(R.id.tvStickerIndex)
        }
    }
}
