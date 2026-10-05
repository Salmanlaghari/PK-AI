package com.salmanlaghari.pkai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache

/**
 * Loads Super Chat avatar stickers bundled in `assets/poses/stickers/`.
 *
 * Files are individually sliced pose images named `pose_001.webp` … `pose_NNN.webp`,
 * numbered row-major per source sheet (poses 1-20 = sheet 1, 21-40 = sheet 2, …).
 * The catalogue is discovered at runtime via [android.content.res.AssetManager.list],
 * so dropping more sticker files into the assets folder extends the grid with no
 * code change.
 *
 * When a sticker file is missing, a stylized placeholder is generated
 * programmatically so the UI always works.
 */
object SpriteSheetLoader {

    /**
     * Hard upper bound for grid sizing; actual count comes from the assets folder.
     * Covers the [EXTRA_STICKER_COUNT] virtual emoji stickers sitting past the
     * 18+ SUPER pool (virtual indices 216..243), so [PoseRegistry.allStickers]
     * keeps them all reachable in the picker.
     */
    const val STICKER_COUNT = 244
    private const val MAX_CACHE_SIZE = 48 // individual sticker bitmaps

    /**
     * Extra emoji stickers appended after the bundled assets (virtual indices
     * rendered by [generatePlaceholder] — no asset files needed).
     */
    const val EXTRA_STICKER_COUNT = 28
    /**
     * First virtual (emoji) sticker index. Sits just past the 18+ SUPER pool
     * (200..215) so existing placeholder indices keep their legacy look.
     */
    private const val VIRTUAL_STICKER_START = 216

    private val cellCache = object : LruCache<Int, Bitmap>(MAX_CACHE_SIZE) {}

    /** Emoji shown on placeholder stickers, cycled. */
    private val placeholderEmoji = listOf(
        "👋", "❤️", "👍", "☝️", "✌️", "😘", "💃", "🧘", "🎉", "🖥️",
        "👏", "🤔", "🤗", "🤷", "🤸", "😊", "🙅", "🫡", "💪", "🔄"
    )

    /**
     * Themed emoji for the [EXTRA_STICKER_COUNT] extra stickers, in index
     * order starting at the virtual start (216..243 with the current assets).
     * Categories: love/romance, funny, sad, angry, celebration, Desi/Pakistani.
     */
    private val extraStickerEmoji = listOf(
        // Love / romance 💕
        "💕", "😍", "🥰", "💋", "❤️‍🔥",
        // Funny 😂
        "🤣", "😜", "🤪", "😹", "🙈",
        // Sad 😢
        "😢", "😭", "💔", "🥺",
        // Angry 😠
        "😠", "😡", "🤬", "👿",
        // Celebration 🎉
        "🎉", "🥳", "🎊", "🎂", "🪅",
        // Desi / Pakistani 🇵🇰
        "🇵🇰", "🍵", "🏏", "🕌", "🌙"
    )

    private var catalog: List<Int>? = null
    /** First virtual (emoji) sticker index of the current catalogue. */
    private var virtualStart: Int = VIRTUAL_STICKER_START

    /**
     * Sorted list of available sticker indices (0-based): the real sticker
     * files from the assets folder, followed by [EXTRA_STICKER_COUNT] virtual
     * emoji stickers. Falls back to the full 0..199 range when the folder is
     * absent so placeholder stickers still populate the grid.
     */
    fun availableStickers(context: Context): List<Int> {
        catalog?.let { return it }
        val real = try {
            context.assets.list("poses/stickers")
                ?.mapNotNull { name ->
                    Regex("^pose_(\\d+)\\.webp$").find(name)?.groupValues?.get(1)?.toIntOrNull()
                }
                ?.map { it - 1 }
                ?.sorted()
                .orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        val base = real.ifEmpty { IntArray(STICKER_COUNT) { it }.toList() }
        // Virtual emoji stickers start past the real assets AND past the 18+
        // SUPER pool (200..215), so dropping more pose_*.webp files later can
        // never collide with them and nothing existing shifts.
        virtualStart = maxOf((base.maxOrNull() ?: -1) + 1, VIRTUAL_STICKER_START)
        val result = base + (virtualStart until virtualStart + EXTRA_STICKER_COUNT).toList()
        catalog = result
        return result
    }

    /** True when at least one real sticker is bundled in assets. */
    fun hasAnyRealSheet(context: Context): Boolean =
        availableStickers(context).isNotEmpty()

    /**
     * Returns the sticker bitmap for [index] (0-based). Never null — falls back to a
     * generated placeholder when the file is not bundled.
     */
    fun getSticker(context: Context, index: Int): Bitmap {
        val available = availableStickers(context)
        val resolved = if (index < available.size) available[index] else index
        cellCache.get(resolved)?.let { return it }

        val bitmap = loadSticker(context, resolved) ?: generatePlaceholder(resolved)
        cellCache.put(resolved, bitmap)
        return bitmap
    }

    private fun loadSticker(context: Context, index: Int): Bitmap? = try {
        context.assets.open("poses/stickers/pose_%03d.webp".format(index + 1)).use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Emoji drawn on a placeholder card. Extra (virtual) stickers use their own
     * themed emoji; older indices keep the legacy cycle so nothing existing
     * changes appearance.
     */
    private fun emojiFor(index: Int): String {
        val extra = index - virtualStart
        if (extra in extraStickerEmoji.indices) return extraStickerEmoji[extra]
        return placeholderEmoji[index % placeholderEmoji.size]
    }

    /**
     * Generates a stylized placeholder sticker so the UI is fully functional before
     * real sticker assets are bundled.
     */
    private fun generatePlaceholder(index: Int): Bitmap {
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Dark card background with purple gradient border feel.
        paint.shader = LinearGradient(
            0f, 0f, 0f, size.toFloat(),
            Color.parseColor("#14102A"), Color.parseColor("#0A0612"), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null

        paint.color = Color.parseColor("#7B2FFD")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawRoundRect(6f, 6f, size - 6f, size - 6f, 24f, 24f, paint)

        // Glowing cyan ring near the bottom, echoing the avatar stage.
        paint.color = Color.parseColor("#00D4FF")
        paint.strokeWidth = 6f
        paint.maskFilter = null
        canvas.drawOval(RectF(70f, size - 78f, size - 70f, size - 30f), paint)

        // Big emoji "pose".
        val emoji = emojiFor(index)
        paint.color = Color.WHITE
        paint.textSize = 110f
        paint.textAlign = Paint.Align.CENTER
        val textBounds = Rect()
        paint.getTextBounds(emoji, 0, emoji.length, textBounds)
        canvas.drawText(
            emoji, size / 2f,
            size / 2f - textBounds.exactCenterY() - 10f, paint
        )

        // Sticker number tag.
        paint.textSize = 26f
        paint.color = Color.parseColor("#C4B5FD")
        canvas.drawText("#${index + 1}", size / 2f, size - 14f, paint)
        return bitmap
    }
}
