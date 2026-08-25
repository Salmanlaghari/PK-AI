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
import java.io.InputStream

/**
 * Loads Super Chat avatar stickers from sprite sheets bundled in `assets/poses/`.
 *
 * Expected files: `poses_sheet_1.png` … `poses_sheet_10.png`, each a uniform
 * **5 × 4 grid = 20 cells** (numbered row-major, left→right, top→bottom), so
 * sticker index *i* lives on sheet `i / 20 + 1` at column `i % 5`, row `i / 5 % 4`.
 *
 * When a sheet file is missing, a stylized placeholder sticker is generated
 * programmatically so the UI always works. When real sheets are dropped into
 * `assets/poses/` they are picked up automatically — no code change needed.
 */
object SpriteSheetLoader {

    const val STICKER_COUNT = 200
    const val COLS = 5
    const val ROWS = 4
    private const val CELLS_PER_SHEET = COLS * ROWS
    private const val MAX_SHEETS = STICKER_COUNT / CELLS_PER_SHEET
    private const val MAX_CACHE_SIZE = 48 // individual sticker bitmaps

    private val sheetCache = object : LruCache<String, Bitmap?>(MAX_SHEETS) {}
    private val cellCache = object : LruCache<Int, Bitmap>(MAX_CACHE_SIZE) {}

    /** Emoji shown on placeholder stickers, one per mood in [Mood] order, cycled. */
    private val placeholderEmoji = listOf(
        "👋", "❤️", "👍", "☝️", "✌️", "😘", "💃", "🧘", "🎉", "🖥️",
        "👏", "🤔", "🤗", "🤷", "🤸", "😊", "🙅", "🫡", "💪", "🔄"
    )

    /**
     * Returns the sticker bitmap for [index] (0-based). Never null — falls back to a
     * generated placeholder when the sheet is not bundled.
     */
    fun getSticker(context: Context, index: Int): Bitmap {
        val safeIndex = index.coerceIn(0, STICKER_COUNT - 1)
        cellCache.get(safeIndex)?.let { return it }

        val sheetIndex = safeIndex / CELLS_PER_SHEET
        val cell = safeIndex % CELLS_PER_SHEET
        val col = cell % COLS
        val row = cell / COLS

        val sheet = loadSheet(context, sheetIndex + 1)
        val bitmap = if (sheet != null) {
            Bitmap.createBitmap(
                sheet,
                col * sheet.width / COLS,
                row * sheet.height / ROWS,
                sheet.width / COLS,
                sheet.height / ROWS
            )
        } else {
            generatePlaceholder(safeIndex)
        }
        cellCache.put(safeIndex, bitmap)
        return bitmap
    }

    /** True when the real sheet file for [sheetNumber] (1-based) is bundled. */
    fun isSheetBundled(context: Context, sheetNumber: Int): Boolean = try {
        context.assets.open(sheetAssetName(sheetNumber)).use { true }
    } catch (_: Exception) {
        false
    }

    /** True when at least one real sheet is bundled in assets. */
    fun hasAnyRealSheet(context: Context): Boolean =
        (1..MAX_SHEETS).any { isSheetBundled(context, it) }

    private fun sheetAssetName(sheetNumber: Int) = "poses/poses_sheet_$sheetNumber.png"

    private fun loadSheet(context: Context, sheetNumber: Int): Bitmap? {
        val key = "sheet_$sheetNumber"
        if (sheetCache.get(key) != null) return sheetCache.get(key)

        val bitmap = try {
            context.assets.open(sheetAssetName(sheetNumber)).use { stream: InputStream ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)
                // Downsample large sheets — cells only need ~300px each for crisp display.
                var sample = 1
                while (opts.outHeight / (sample * 2) >= ROWS * 300) sample *= 2
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.assets.open(sheetAssetName(sheetNumber)).use { s2 ->
                    BitmapFactory.decodeStream(s2, null, decodeOpts)
                }
            }
        } catch (_: Exception) {
            null
        }
        sheetCache.put(key, bitmap)
        return bitmap
    }

    /**
     * Generates a stylized placeholder sticker so the UI is fully functional before
     * real sprite sheets are bundled.
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
