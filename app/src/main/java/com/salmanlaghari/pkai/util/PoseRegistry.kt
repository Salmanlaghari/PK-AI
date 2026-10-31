package com.salmanlaghari.pkai.util

/**
 * Central, explicitly allowlisted catalogue for Super Chat reaction stickers.
 * Human pose artwork has been removed pending an image-safety review; only the
 * built-in neutral emoji reactions below are selectable or used in rotation.
 */
object PoseRegistry {
    private const val FIRST_SAFE_STICKER_INDEX = 216
    private const val SAFE_STICKER_COUNT = 28

    val defaultSticker: Int = FIRST_SAFE_STICKER_INDEX
    val safeStickerIndices: List<Int> =
        (FIRST_SAFE_STICKER_INDEX until FIRST_SAFE_STICKER_INDEX + SAFE_STICKER_COUNT).toList()

    val moodStickers: Map<Mood, List<Int>> = mapOf(
        Mood.GREETING to listOf(216, 221, 228, 229),
        Mood.HAPPY to listOf(221, 230, 234, 235),
        Mood.GRATEFUL to listOf(220, 224, 225),
        Mood.SAD to listOf(236, 237, 238, 232),
        Mood.LOVE to listOf(217, 218, 220, 221),
        Mood.FAREWELL to listOf(216, 229, 243),
        Mood.EXCITED to listOf(221, 234, 235, 241),
        Mood.AGREE to listOf(218, 219, 229),
        Mood.DISAGREE to listOf(225, 232, 233),
        Mood.ANGRY to listOf(239, 240, 232),
        Mood.THINKING to listOf(223, 225, 228),
        Mood.NEUTRAL to listOf(216, 219, 223, 225, 228, 229, 230, 242)
    )

    val moodSticker: Map<Mood, Int> =
        Mood.entries.associateWith { mood -> moodStickers[mood]?.firstOrNull() ?: defaultSticker }

    fun isSafeSticker(index: Int): Boolean = index in safeStickerIndices

    fun sanitizeSticker(index: Int): Int =
        if (isSafeSticker(index)) index else defaultSticker

    fun stickerForMood(mood: Mood, rotation: Int = 0): Int {
        val candidates = moodStickers[mood].orEmpty().filter(::isSafeSticker)
        if (candidates.isEmpty()) return defaultSticker
        return candidates[rotation.mod(candidates.size)]
    }

    /** Only the allowlisted emoji reactions are exposed in the picker. */
    fun allStickers(): IntArray = safeStickerIndices.toIntArray()
}
