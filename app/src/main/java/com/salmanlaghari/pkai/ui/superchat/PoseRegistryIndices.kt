package com.salmanlaghari.pkai.ui.superchat

import android.content.Context
import com.salmanlaghari.pkai.util.SpriteSheetLoader

/** Helper that resolves which sticker indices the picker grid should show. */
internal object PoseRegistryIndices {

    /**
     * Returns the sticker indices for the picker. When [favoritesOnly] is true and
     * [favorites] is non-empty, only those are shown; otherwise the full catalogue
     * discovered from the bundled sticker assets.
     */
    fun forPicker(context: Context, favorites: Set<Int>, favoritesOnly: Boolean): IntArray {
        return if (favoritesOnly && favorites.isNotEmpty()) {
            favorites.toIntArray().also { it.sort() }
        } else {
            SpriteSheetLoader.availableStickers(context).toIntArray()
        }
    }
}
