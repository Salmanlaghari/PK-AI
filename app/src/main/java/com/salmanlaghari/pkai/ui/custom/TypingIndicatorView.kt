package com.salmanlaghari.pkai.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class TypingIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00F5FF") // HTML Cyan
        style = Paint.Style.FILL
    }

    private var dotRadius = 0f
    private var dotSpacing = 0f
    private var bounceRange = 0f
    private var animationTime = 0f

    init {
        val density = context.resources.displayMetrics.density
        dotRadius = 4f * density
        dotSpacing = 6f * density
        bounceRange = 6f * density
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = context.resources.displayMetrics.density
        val desiredWidth = ((dotRadius * 2 * 3) + (dotSpacing * 2) + (density * 16)).toInt()
        val desiredHeight = ((dotRadius * 2) + bounceRange + (density * 16)).toInt()

        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width
        val h = height
        if (w == 0 || h == 0) return

        // Global animation timer increment
        animationTime += 0.15f

        // Center coordinates
        val centerY = h / 2f
        val totalWidth = (dotRadius * 2 * 3) + (dotSpacing * 2)
        val startX = (w - totalWidth) / 2f + dotRadius

        for (i in 0 until 3) {
            // Staggered bounce calculation using a sine wave with phase offset
            val phaseOffset = i * 0.8f
            val bounceMultiplier = sin(animationTime - phaseOffset)

            // Only bounce upwards (matching the CSS bounce animation)
            val yOffset = if (bounceMultiplier < 0) bounceMultiplier * bounceRange else 0f
            val x = startX + i * (dotRadius * 2 + dotSpacing)
            val y = centerY + yOffset

            // Set alpha proportional to bounce state (matching typingBounce opacity variation)
            val alphaMultiplier = (bounceMultiplier + 1f) / 2f // 0.0 to 1.0
            val alpha = (100 + (alphaMultiplier * 155)).toInt() // Range 100 to 255
            dotPaint.alpha = alpha

            canvas.drawCircle(x, y, dotRadius, dotPaint)
        }

        // Loop animation at 60fps
        postInvalidateOnAnimation()
    }
}
