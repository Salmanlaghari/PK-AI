package com.salmanlaghari.pkai.ui.superchat

import android.content.Context
import com.salmanlaghari.pkai.util.PoseRegistry

/** Resolves picker indices while dropping favorites from retired character artwork. */
internal object PoseRegistryIndices {
    @Suppress("UNUSED_PARAMETER")
    fun forPicker(context: Context, favorites: Set<Int>, favoritesOnly: Boolean): IntArray {
        val safeFavorites = favorites.filter(PoseRegistry::isSafeSticker).sorted()
        return if (favoritesOnly && safeFavorites.isNotEmpty()) {
            safeFavorites.toIntArray()
        } else {
            PoseRegistry.allStickers()
        }
    }
}
