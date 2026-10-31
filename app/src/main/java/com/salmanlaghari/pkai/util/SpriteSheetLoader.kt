package com.salmanlaghari.pkai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache

/**
 * Renders the allowlisted Super Chat emoji reactions. Character pose artwork
 * is intentionally not loaded into the app while its safety is under review.
 */
object SpriteSheetLoader {
    /** Legacy catalogue size retained for compatibility with stored indices. */
    const val STICKER_COUNT = 244
    const val EXTRA_STICKER_COUNT = 28
    const val FIRST_SAFE_STICKER_INDEX = 216
    private const val MAX_CACHE_SIZE = 48

    private val cellCache = object : LruCache<Int, Bitmap>(MAX_CACHE_SIZE) {}

    private val safeEmoji = listOf(
        "👋", "❤️", "👍", "✌️", "🙏", "🎉", "👏", "🤔", "🤗", "😊",
        "🫡", "💪", "✨", "📚", "😂", "😢", "😭", "🥺", "😠", "😡",
        "🎊", "🇵🇰", "🍵", "🏏", "🕌", "🌙", "🌟", "🕊️"
    )

    /** Always returns the stable, safe virtual emoji IDs; bundled pose assets are ignored. */
    @Suppress("UNUSED_PARAMETER")
    fun availableStickers(context: Context): List<Int> =
        (FIRST_SAFE_STICKER_INDEX until FIRST_SAFE_STICKER_INDEX + EXTRA_STICKER_COUNT).toList()

    /** Human character sheets are not currently bundled or rendered. */
    @Suppress("UNUSED_PARAMETER")
    fun hasAnyRealSheet(context: Context): Boolean = false

    /** Returns an emoji card even when a stale stored index refers to retired artwork. */
    @Suppress("UNUSED_PARAMETER")
    fun getSticker(context: Context, index: Int): Bitmap {
        val resolved = if (index in FIRST_SAFE_STICKER_INDEX until FIRST_SAFE_STICKER_INDEX + EXTRA_STICKER_COUNT) {
            index
        } else {
            FIRST_SAFE_STICKER_INDEX
        }
        cellCache.get(resolved)?.let { return it }
        return generatePlaceholder(resolved).also { cellCache.put(resolved, it) }
    }

    private fun generatePlaceholder(index: Int): Bitmap {
        val size = 300
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

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

        paint.color = Color.parseColor("#00D4FF")
        paint.strokeWidth = 6f
        paint.maskFilter = null
        canvas.drawOval(RectF(70f, size - 78f, size - 70f, size - 30f), paint)

        val emojiIndex = (index - FIRST_SAFE_STICKER_INDEX).coerceIn(safeEmoji.indices)
        val emoji = safeEmoji[emojiIndex]
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        paint.textSize = 110f
        paint.textAlign = Paint.Align.CENTER
        val textBounds = Rect()
        paint.getTextBounds(emoji, 0, emoji.length, textBounds)
        canvas.drawText(
            emoji,
            size / 2f,
            size / 2f - textBounds.exactCenterY() - 10f,
            paint
        )

        paint.textSize = 26f
        paint.color = Color.parseColor("#C4B5FD")
        canvas.drawText("#${index + 1}", size / 2f, size - 14f, paint)
        return bitmap
    }
}
