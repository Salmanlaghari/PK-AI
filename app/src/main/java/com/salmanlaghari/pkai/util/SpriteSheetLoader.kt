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

    /** Hard upper bound for grid sizing; actual count comes from the assets folder. */
    const val STICKER_COUNT = 220
    private const val MAX_CACHE_SIZE = 48 // individual sticker bitmaps

    private val cellCache = object : LruCache<Int, Bitmap>(MAX_CACHE_SIZE) {}

    /** Emoji shown on placeholder stickers, cycled. */
    private val placeholderEmoji = listOf(
        "👋", "❤️", "👍", "☝️", "✌️", "😘", "💃", "🧘", "🎉", "🖥️",
        "👏", "🤔", "🤗", "🤷", "🤸", "😊", "🙅", "🫡", "💪", "🔄"
    )

    private var catalog: List<Int>? = null

    /**
     * Sorted list of available sticker indices (0-based), read from the assets
     * folder. Falls back to the full 0..199 range when the folder is absent so
     * placeholder stickers still populate the grid.
     */
    fun availableStickers(context: Context): List<Int> {
        catalog?.let { return it }
        val list = try {
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
        val result = list.ifEmpty { IntArray(STICKER_COUNT) { it }.toList() }
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
        val emoji = placeholderEmoji[index % placeholderEmoji.size]
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
