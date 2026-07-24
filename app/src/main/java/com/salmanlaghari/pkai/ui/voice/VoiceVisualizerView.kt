package com.salmanlaghari.pkai.ui.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.random.Random

class VoiceVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val numBars = 35
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectF = RectF()

    // Real-time animated heights and targets
    private val currentHeights = FloatArray(numBars)
    private val targetHeights = FloatArray(numBars)

    private val minBarHeight = 10f // Minimal visible height so it doesn't look completely empty
    private var isInitialized = false

    // Multi-color gradients for the bars (Cyan -> Magenta -> Purple)
    private var gradient: LinearGradient? = null

    init {
        // Init heights
        for (i in 0 until numBars) {
            currentHeights[i] = minBarHeight
            targetHeights[i] = minBarHeight
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            // Setup vertical gradient (Cyan at top, Magenta/Purple in middle/bottom)
            gradient = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(0xFF00F0FF.toInt(), 0xFFFF00C8.toInt(), 0xFF7928CA.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
            barPaint.shader = gradient
            isInitialized = true
        }
    }

    /**
     * Call this with a raw amplitude (normalized or root mean square dB) to drive wave animation.
     * amplitude should be on a scale of roughly 0.0 to 1.0 (or we will safely normalize it).
     */
    fun updateAmplitude(amplitude: Float) {
        // Clamp and normalize amplitude
        val normalized = amplitude.coerceIn(0f, 1f)

        // Calculate heights centered around the middle bar (index 17)
        val centerIndex = numBars / 2
        val maxHeight = height.toFloat() * 0.9f

        for (i in 0 until numBars) {
            // Distance from center
            val distanceFromCenter = abs(i - centerIndex)
            // Gaussian-like decay factor
            val decay = Math.exp(-distanceFromCenter.toDouble() * distanceFromCenter.toDouble() / 25.0).toFloat()

            // Calculate base target height
            var target = minBarHeight + (maxHeight * normalized * decay)

            // Add a touch of organic micro-randomness for real voice assistant aesthetics
            if (normalized > 0.05f) {
                val noise = (Random.nextFloat() - 0.5f) * maxHeight * 0.15f * decay
                target += noise
            }

            targetHeights[i] = target.coerceIn(minBarHeight, maxHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isInitialized) return

        val w = width.toFloat()
        val h = height.toFloat()

        // Calculate optimal spacing and width
        val gap = 6f
        val totalGaps = gap * (numBars - 1)
        val barWidth = (w - totalGaps) / numBars

        // 60fps Interpolation loop
        for (i in 0 until numBars) {
            // Smoothly ease current height towards target (150ms ease, about 0.2f interpolation speed per frame)
            currentHeights[i] += (targetHeights[i] - currentHeights[i]) * 0.2f

            // Calculate draw coordinates
            val xStart = i * (barWidth + gap)
            val xEnd = xStart + barWidth
            val yStart = (h - currentHeights[i]) / 2f
            val yEnd = yStart + currentHeights[i]

            rectF.set(xStart, yStart, xEnd, yEnd)
            // Draw a highly premium rounded capsule-style bar
            canvas.drawRoundRect(rectF, barWidth / 2f, barWidth / 2f, barPaint)
        }

        // Loop draw animation continuously at 60fps
        postInvalidateOnAnimation()
    }
}
