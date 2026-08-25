package com.salmanlaghari.pkai.ui.superchat

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.salmanlaghari.pkai.R

/**
 * "4D/5D" motion system for Super Chat avatar stickers.
 *
 * Each pose gets a looping motion style that matches its artwork, so stickers
 * feel alive instead of static:
 *
 *  - [Style.SHAKE]  — hand-shake rock around the feet (wave / salute poses)
 *  - [Style.BOUNCE] — springy hop (jump / excited / fist-pump poses)
 *  - [Style.PULSE]  — loving heartbeat scale (heart-hands / kiss / clap poses)
 *  - [Style.TILT]   — thoughtful head tilt (thinking / shy poses)
 *  - [Style.SWAY]   — gentle idle sway (everything else)
 */
object StickerMotion {

    enum class Style { SHAKE, BOUNCE, PULSE, TILT, SWAY }

    private val styles = Style.entries.toTypedArray()

    /** Poses from sheet 1 (indices 0-19) with semantically matched motion. */
    private val sheet1Motion: Map<Int, Style> = mapOf(
        0 to Style.SHAKE,   // wave
        1 to Style.PULSE,   // heart hands
        2 to Style.BOUNCE,  // thumbs up
        3 to Style.SWAY,    // pointing
        4 to Style.BOUNCE,  // peace
        5 to Style.PULSE,   // kiss
        6 to Style.SWAY,    // hands on hips
        7 to Style.SWAY,    // sitting
        8 to Style.BOUNCE,  // jump
        9 to Style.SWAY,    // hologram
        10 to Style.PULSE,  // clap
        11 to Style.TILT,   // thinking
        12 to Style.SHAKE,  // welcome
        13 to Style.BOUNCE, // excited
        14 to Style.BOUNCE, // cheer
        15 to Style.TILT,   // shy
        16 to Style.SWAY,   // arms crossed
        17 to Style.SHAKE,  // salute
        18 to Style.BOUNCE, // fist pump
        19 to Style.SWAY    // back view
    )

    /** Picks the motion style for a sticker index. */
    fun styleFor(index: Int): Style =
        sheet1Motion[index] ?: styles[index % styles.size]

    /**
     * Starts the looping motion for [style] on [view]. The pivot is placed at the
     * bottom-centre so rotation rocks around the character's feet. Cancels any
     * motion previously started on this view via [stop].
     */
    fun start(view: View, style: Style) {
        stop(view)
        view.post {
            if (!view.isAttachedToWindow) return@post
            view.pivotX = view.width / 2f
            view.pivotY = view.height.toFloat()

            val animators = when (style) {
                Style.SHAKE -> listOf(
                    ObjectAnimator.ofFloat(view, View.ROTATION, -7f, 7f).apply {
                        duration = 650
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.REVERSE
                        interpolator = AccelerateDecelerateInterpolator()
                    }
                )
                Style.BOUNCE -> listOf(
                    ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, -16f).apply {
                        duration = 420
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.REVERSE
                        interpolator = OvershootInterpolator(0.6f)
                    }
                )
                Style.PULSE -> listOf(
                    ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.06f).apply {
                        duration = 850
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.REVERSE
                    },
                    ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.06f).apply {
                        duration = 850
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.REVERSE
                    }
                )
                Style.TILT -> listOf(
                    ObjectAnimator.ofFloat(view, View.ROTATION, 0f, -6f).apply {
                        duration = 1300
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.REVERSE
                        interpolator = AccelerateDecelerateInterpolator()
                    }
                )
                Style.SWAY -> listOf(
                    ObjectAnimator.ofFloat(view, View.ROTATION, -3f, 3f).apply {
                        duration = 1700
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.REVERSE
                        interpolator = AccelerateDecelerateInterpolator()
                    }
                )
            }
            val set = AnimatorSet()
            set.playTogether(animators)
            set.start()
            view.setTag(R.id.tag_sticker_motion, set)
        }
    }

    /** Cancels and clears any running motion on [view]. */
    fun stop(view: View) {
        (view.getTag(R.id.tag_sticker_motion) as? AnimatorSet)?.let {
            it.cancel()
            view.setTag(R.id.tag_sticker_motion, null)
        }
        view.animate().cancel()
        view.rotation = 0f
        view.translationY = 0f
        view.scaleX = 1f
        view.scaleY = 1f
    }
}
