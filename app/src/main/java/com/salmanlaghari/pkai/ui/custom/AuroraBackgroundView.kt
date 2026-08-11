package com.salmanlaghari.pkai.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class AuroraBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Base background color #03050A
    private val baseColor = Color.parseColor("#03050A")

    // Paint for drawing background and gradients
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Twinkling stars properties
    private class Star(
        var x: Float,
        var y: Float,
        var size: Float,
        var phase: Float,
        var speed: Float
    )

    private val stars = ArrayList<Star>()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    // Floating blurred circles properties
    private class FloatingBubble(
        var x: Float,
        var y: Float,
        var targetX: Float,
        var targetY: Float,
        var radius: Float,
        var speed: Float,
        var baseAlpha: Int, // Out of 255
        var colorHex: String
    )

    private val bubbles = ArrayList<FloatingBubble>()

    // Global animation clock
    private var animationTime = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return

        // 1. Generate Stars
        stars.clear()
        val numStars = 45
        val density = dpToPx()
        for (i in 0 until numStars) {
            stars.add(
                Star(
                    x = (Math.random() * w).toFloat(),
                    y = (Math.random() * h).toFloat(),
                    size = (density + Math.random().toFloat() * 2f * density),
                    phase = (Math.random() * Math.PI * 2).toFloat(),
                    speed = (0.01f + Math.random().toFloat() * 0.03f)
                )
            )
        }

        // 2. Generate Floating Bubbles (from HTML design spec: cyan, purple, pink)
        bubbles.clear()
        // Cyan Bubble
        bubbles.add(
            FloatingBubble(
                x = w * 0.2f, y = h * 0.4f,
                targetX = w * 0.2f, targetY = h * 0.4f,
                radius = w * 0.6f, speed = 0.0008f,
                baseAlpha = 22, // ~0.08 in HTML
                colorHex = "#00F5FF"
            )
        )
        // Purple Bubble
        bubbles.add(
            FloatingBubble(
                x = w * 0.8f, y = h * 0.2f,
                targetX = w * 0.8f, targetY = h * 0.2f,
                radius = w * 0.5f, speed = 0.0006f,
                baseAlpha = 20, // ~0.08 in HTML
                colorHex = "#8B2EFF"
            )
        )
        // Pink Bubble
        bubbles.add(
            FloatingBubble(
                x = w * 0.6f, y = h * 0.8f,
                targetX = w * 0.6f, targetY = h * 0.8f,
                radius = w * 0.45f, speed = 0.001f,
                baseAlpha = 15, // ~0.06 in HTML
                colorHex = "#FF2FD0"
            )
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width
        val h = height
        if (w == 0 || h == 0) return

        // Increment dynamic animation clock
        animationTime += 0.01f

        // 1. Draw Base Dark Theme Layer
        canvas.drawColor(baseColor)

        // 2. Update and Draw Animated Aurora Radial Gradients
        for (bubble in bubbles) {
            // Animate bubble centers slowly using smooth sine waves for a continuous float effect
            val angleX = animationTime * 15f * bubble.speed
            val angleY = animationTime * 12f * bubble.speed

            val offsetX = sin(angleX) * (w * 0.08f)
            val offsetY = sin(angleY) * (h * 0.08f)

            val drawX = bubble.x + offsetX
            val drawY = bubble.y + offsetY

            // Create gradient for current bubble position
            val centerColor = adjustAlpha(Color.parseColor(bubble.colorHex), bubble.baseAlpha)
            val colors = intArrayOf(centerColor, Color.TRANSPARENT)
            val stops = floatArrayOf(0f, 1f)

            val radialGradient = RadialGradient(
                drawX, drawY, bubble.radius,
                colors, stops, Shader.TileMode.CLAMP
            )

            paint.shader = radialGradient
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
        paint.shader = null // Clear shader for other drawings

        // 3. Draw Twinkling Stars
        for (star in stars) {
            star.phase += star.speed
            val rawSin = sin(star.phase)
            // Map sine to 0.2 - 0.9 alpha
            val alpha = ((rawSin + 1f) / 2f * 0.7f + 0.2f) * 255
            starPaint.alpha = alpha.toInt()

            canvas.drawCircle(star.x, star.y, star.size, starPaint)
        }

        // Loop animation at 60fps
        postInvalidateOnAnimation()
    }

    private fun adjustAlpha(color: Int, alphaValue: Int): Int {
        return (alphaValue and 0xFF shl 24) or (color and 0x00FFFFFF)
    }

    private fun dpToPx(): Float {
        return context.resources.displayMetrics.density
    }
}
