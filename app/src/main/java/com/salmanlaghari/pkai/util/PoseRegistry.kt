package com.salmanlaghari.pkai.util

/**
 * Central catalogue of Super Chat avatar stickers.
 *
 * Sticker indices map 1:1 onto the sliced images loaded by [SpriteSheetLoader]
 * (pose 1 = index 0, pose 2 = index 1, …). Each mood maps to a *list* of
 * candidate poses; [stickerForMood] rotates through the list so the avatar
 * changes on every message instead of freezing on a single pose.
 *
 * Sheet 1 pose reference (indices 0-19):
 *  0 wave · 1 heart hands · 2 thumbs up · 3 pointing · 4 peace · 5 kiss ·
 *  6 hands on hips · 7 sitting · 8 jump · 9 hologram · 10 clap · 11 thinking ·
 *  12 welcome · 13 excited · 14 cheer · 15 shy · 16 arms crossed · 17 salute ·
 *  18 fist pump · 19 back view
 */
object PoseRegistry {

    /** The pose shown when Super Chat opens. */
    val defaultSticker: Int = 0

    /** Candidate poses per mood — [stickerForMood] cycles through these. */
    val moodStickers: Map<Mood, List<Int>> = mapOf(
        Mood.GREETING to listOf(0, 12, 10, 17),          // wave, welcome, clap, salute
        Mood.HAPPY to listOf(3, 18, 13, 4),              // pointing, fist pump, excited, peace
        Mood.GRATEFUL to listOf(1, 10, 5),               // heart hands, clap, kiss
        Mood.SAD to listOf(15, 16, 7),                   // shy, arms crossed, sitting
        Mood.LOVE to listOf(5, 1, 6),                    // kiss, heart hands, hips
        Mood.FAREWELL to listOf(0, 17, 12),              // wave, salute, welcome
        Mood.EXCITED to listOf(8, 14, 4, 18),            // jump, cheer, peace, fist pump
        Mood.AGREE to listOf(2, 6, 17),                  // thumbs up, hips, salute
        Mood.DISAGREE to listOf(16, 15, 11),             // crossed, shy, thinking
        Mood.ANGRY to listOf(16, 15, 19),                // crossed, shy, back view
        Mood.THINKING to listOf(11, 15, 9),              // thinking, shy, hologram
        Mood.NEUTRAL to listOf(
            6, 9, 13, 19,                                 // sheet 1 variety
            22, 27, 33, 38,                               // sheet 2
            44, 50, 55, 59,                               // sheet 3
            63, 70, 77,                                   // sheet 4
            83, 90, 97,                                   // sheet 5
            104, 111, 118,                                // sheet 6
            125, 132, 139,                                // sheet 7
            144, 150, 157,                                // sheet 8
            163, 170,                                     // sheet 9
            182, 190, 197,                                // sheet 10
            200, 205, 210, 215                            // sheet 11 (new)
        )
    )

    /** Legacy single-pose map (first candidate of each mood) for quick lookups. */
    val moodSticker: Map<Mood, Int> =
        Mood.entries.associateWith { moodStickers[it]?.first() ?: defaultSticker }

    /**
     * Returns the pose for [mood], advancing a per-mood rotation counter so
     * consecutive messages show different poses. Pass the value returned by
     * [nextRotation] as [rotation].
     */
    fun stickerForMood(mood: Mood, rotation: Int = 0): Int {
        val candidates = moodStickers[mood].orEmpty()
        if (candidates.isEmpty()) return defaultSticker
        return candidates[rotation.mod(candidates.size)]
    }

    /** /18+ special sticker pool — the new stickers (201+) loaded from pose_201…pose_216. */
    val specialStickers: List<Int> = listOf(
        200, 201, 202, 203, 204, 205, 206, 207,
        208, 209, 210, 211, 212, 213, 214, 215
    )

    /** All sticker indices available in the picker grid. */
    fun allStickers(): IntArray = IntArray(SpriteSheetLoader.STICKER_COUNT) { it }

    /** Returns a random special sticker from the 18+ pool. */
    fun randomSpecialSticker(): Int = specialStickers.random()
}
