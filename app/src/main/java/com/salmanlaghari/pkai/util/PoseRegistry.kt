package com.salmanlaghari.pkai.util

/**
 * Central catalogue of Super Chat avatar stickers.
 *
 * Sticker indices map 1:1 onto the sprite-sheet cells loaded by [SpriteSheetLoader]
 * (sheet 1 holds stickers 1-20, sheet 2 holds 21-40, and so on). The mood mapping
 * below points each [Mood] at the sticker index that best matches the emotion —
 * edit the numbers here if the artwork order changes; no other code needs touching.
 *
 * Default mapping follows the reference sheet numbering:
 *  1 wave · 2 heart hands · 3 thumbs up · 4 pointing · 5 peace · 6 kiss ·
 *  7 hands on hips · 8 sitting · 9 jump · 10 hologram · 11 clap · 12 thinking ·
 *  13 welcome · 14 excited · 15 cheer · 16 shy · 17 arms crossed · 18 salute ·
 *  19 fist pump · 20 back view
 */
object PoseRegistry {

    /** Sticker index (0-based) shown for each mood. */
    val moodSticker: Map<Mood, Int> = mapOf(
        Mood.GREETING to 0,    // 1  — wave
        Mood.GRATEFUL to 1,    // 2  — heart hands
        Mood.AGREE to 2,       // 3  — thumbs up
        Mood.HAPPY to 3,       // 4  — pointing
        Mood.EXCITED to 4,     // 5  — peace
        Mood.LOVE to 5,        // 6  — kiss
        Mood.NEUTRAL to 6,     // 7  — hands on hips
        Mood.SAD to 15,        // 16 — shy/head down
        Mood.THINKING to 11,   // 12 — thinking
        Mood.DISAGREE to 16,   // 17 — arms crossed
        Mood.ANGRY to 16,      // 17 — arms crossed
        Mood.FAREWELL to 0     // 1  — wave
    )

    /** The pose shown when Super Chat opens. */
    val defaultSticker: Int = 0

    fun stickerForMood(mood: Mood): Int = moodSticker[mood] ?: defaultSticker

    /** All sticker indices available in the picker grid. */
    fun allStickers(): IntArray = IntArray(SpriteSheetLoader.STICKER_COUNT) { it }
}
