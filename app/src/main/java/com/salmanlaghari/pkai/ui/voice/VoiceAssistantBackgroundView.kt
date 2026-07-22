package com.salmanlaghari.pkai.ui.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class VoiceAssistantBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
    }
    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Cosmic base colors
    private val cosmicStart = 0xFF050510.toInt() // #050510 with alpha
    private val cosmicMiddle = 0xFF080818.toInt() // #080818
    private val cosmicEnd = 0xFF101030.toInt() // #101030

    // Stars collection
    private data class Star(
        val xRatio: Float,
        val yRatio: Float,
        val size: Float,
        var alphaPhase: Float,
        val speed: Float
    )

    private val stars = ArrayList<Star>()
    private var isInitialized = false

    // Orbs animation properties
    private var time = 0f

    init {
        // Generate 125 stars
        for (i in 0 until 125) {
            stars.add(
                Star(
                    xRatio = Random.nextFloat(),
                    yRatio = Random.nextFloat(),
                    size = Random.nextFloat() * 2f + 1f, // 1 to 3px
                    alphaPhase = Random.nextFloat() * Math.PI.toFloat() * 2f,
                    speed = Random.nextFloat() * 0.05f + 0.02f
                )
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            // Background cosmic linear gradient from top-left to bottom-right
            val gradient = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(cosmicEnd, cosmicMiddle, 0xFF050510.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
            bgPaint.shader = gradient
            isInitialized = true
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isInitialized) return

        val w = width.toFloat()
        val h = height.toFloat()

        // 1. Draw cosmic base background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Increment time for animations
        time += 0.016f // Roughly 60fps increments

        // 2. Draw 3 Floating neon blurred orbs
        // Orb 1: Cyan glow (#00f0ff), top-left area, moving gently in small circle
        val orb1X = w * 0.25f + Math.sin(time.toDouble() * 0.5).toFloat() * 40f
        val orb1Y = h * 0.20f + Math.cos(time.toDouble() * 0.5).toFloat() * 40f
        val orb1Radius = w * 0.45f
        val shader1 = RadialGradient(
            orb1X, orb1Y, orb1Radius,
            intArrayOf(0x5000F0FF.toInt(), 0x1500F0FF.toInt(), 0x00000000),
            null, Shader.TileMode.CLAMP
        )
        orbPaint.shader = shader1
        canvas.drawCircle(orb1X, orb1Y, orb1Radius, orbPaint)

        // Orb 2: Magenta glow (#ff00c8), top-right, moving slightly different
        val orb2X = w * 0.80f + Math.cos(time.toDouble() * 0.4).toFloat() * 50f
        val orb2Y = h * 0.30f + Math.sin(time.toDouble() * 0.4).toFloat() * 50f
        val orb2Radius = w * 0.40f
        val shader2 = RadialGradient(
            orb2X, orb2Y, orb2Radius,
            intArrayOf(0x40FF00C8.toInt(), 0x10FF00C8.toInt(), 0x00000000),
            null, Shader.TileMode.CLAMP
        )
        orbPaint.shader = shader2
        canvas.drawCircle(orb2X, orb2Y, orb2Radius, orbPaint)

        // Orb 3: Purple glow (#7928ca), bottom-left
        val orb3X = w * 0.30f + Math.sin(time.toDouble() * 0.3).toFloat() * 60f
        val orb3Y = h * 0.75f + Math.cos(time.toDouble() * 0.3).toFloat() * 60f
        val orb3Radius = w * 0.50f
        val shader3 = RadialGradient(
            orb3X, orb3Y, orb3Radius,
            intArrayOf(0x357928CA.toInt(), 0x0F7928CA.toInt(), 0x00000000),
            null, Shader.TileMode.CLAMP
        )
        orbPaint.shader = shader3
        canvas.drawCircle(orb3X, orb3Y, orb3Radius, orbPaint)

        // 3. Draw 120+ Twinkling stars
        stars.forEach { star ->
            val starX = star.xRatio * w
            val starY = star.yRatio * h
            // Update phase for individual twinkling
            star.alphaPhase += star.speed
            val rawAlpha = (Math.sin(star.alphaPhase.toDouble()) + 1.0) / 2.0 // 0.0 to 1.0
            val starAlpha = (rawAlpha * 255).toInt().coerceIn(40, 255)

            starPaint.alpha = starAlpha
            canvas.drawCircle(starX, starY, star.size, starPaint)
        }

        // Loop draw animation continuously at 60fps
        postInvalidateOnAnimation()
    }
}
