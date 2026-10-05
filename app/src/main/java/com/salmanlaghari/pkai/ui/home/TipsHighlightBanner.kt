package com.salmanlaghari.pkai.ui.home

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.salmanlaghari.pkai.R

/**
 * Contextual "Tips Highlights" popup.
 *
 * Shows a card at the top of the screen for ~5 seconds with 2-3 tips
 * RELATED to what the user just searched/asked (song search → music tips,
 * code question → coding tips, etc.). Slides down, auto-dismisses, and can
 * be tapped to dismiss early.
 *
 * Self-contained: no fragment changes needed. NOT wired into HomeFragment
 * yet — call from wherever the user's query is handled:
 *
 *     TipsHighlightBanner.show(activity, userQuery)
 *
 * Safe to call from any thread; no-ops if the activity is finishing.
 */
object TipsHighlightBanner {

    private const val TAG = "tips_banner_highlight"
    private const val DISPLAY_MS = 5000L
    private const val ANIM_MS = 280L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingDismiss: Runnable? = null

    /**
     * Shows the tips banner for [query] on top of [activity]'s content.
     * Any banner already showing is replaced.
     */
    fun show(activity: Activity, query: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(activity, query) }
            return
        }
        if (activity.isFinishing || activity.isDestroyed) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        // One banner at a time — drop the previous one without animation.
        dismissInternal(root, animate = false)

        val (title, tips) = tipsFor(query)
        val banner = LayoutInflater.from(activity)
            .inflate(R.layout.banner_tips_highlight, root, false)
        banner.tag = TAG
        banner.findViewById<TextView>(R.id.tvTipsTitle).text = title
        val container = banner.findViewById<LinearLayout>(R.id.llTipsContainer)
        val density = activity.resources.displayMetrics.density
        tips.take(3).forEach { tip ->
            container.addView(TextView(activity).apply {
                text = "• $tip"
                setTextColor(Color.WHITE)
                textSize = 12f
                val vPad = (3 * density).toInt()
                setPadding(0, vPad, 0, vPad)
            })
        }

        val margin = (12 * density).toInt()
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(margin, margin, margin, 0) }
        root.addView(banner, params)

        // Slide down from the top once measured.
        banner.post {
            banner.translationY = -banner.height.toFloat()
            banner.animate()
                .translationY(0f)
                .setDuration(ANIM_MS)
                .start()
        }
        // Tap to dismiss early.
        banner.setOnClickListener { dismissInternal(root, animate = true) }

        // Auto-dismiss after 5 seconds.
        val dismiss = Runnable {
            if (!activity.isFinishing && !activity.isDestroyed) {
                dismissInternal(root, animate = true)
            }
        }
        pendingDismiss?.let { mainHandler.removeCallbacks(it) }
        pendingDismiss = dismiss
        mainHandler.postDelayed(dismiss, DISPLAY_MS)
    }

    /** Removes the banner if present, sliding it back up when [animate]. */
    private fun dismissInternal(root: ViewGroup, animate: Boolean) {
        pendingDismiss?.let { mainHandler.removeCallbacks(it) }
        pendingDismiss = null
        val banner = root.findViewWithTag<View>(TAG) ?: return
        if (!animate || !banner.isLaidOut) {
            root.removeView(banner)
            return
        }
        banner.animate()
            .translationY(-banner.height.toFloat())
            .alpha(0f)
            .setDuration(ANIM_MS)
            .withEndAction {
                try {
                    root.removeView(banner)
                } catch (_: Exception) {
                    // Already removed — nothing to do.
                }
            }
            .start()
    }

    /**
     * Picks a title + up to 3 tips for [query]. All tips are in Roman Urdu,
     * matching the app's conversational norm.
     */
    private fun tipsFor(query: String): Pair<String, List<String>> {
        val q = query.lowercase()
        fun has(vararg words: String) = words.any { q.contains(it) }
        return when {
            has("song", "gana", "gaana", "music", "singer", "sunao", "suno") ->
                "🎵 Music Tips" to listOf(
                    "Gaane ka naam + singer likhein — best results milenge",
                    "Card par ▶ dabayein, song direct chat mein play hoga",
                    "\"play kesariya\" jaisa short command bhi kaam karta hai"
                )
            has("code", "python", "kotlin", "java", "function", "error", "bug", "api") ->
                "💻 Coding Tips" to listOf(
                    "Error ka poora message paste karein — fix tez milega",
                    "Language ka naam zaroor likhein (Kotlin, Python…)",
                    "``` wale code blocks par Run dabakar test kar sakte hain"
                )
            has("image", "photo", "tasveer", "picture", "draw", "banao") ->
                "🎨 Image Tips" to listOf(
                    "Detail mein describe karein — style, colors, mood",
                    "Generated image par tap karke fullscreen dekhein",
                    "Roman Urdu ya English — dono mein prompt chalega"
                )
            has("video") ->
                "🎬 Video Tips" to listOf(
                    "Video ka topic + duration likhein for best result",
                    "Shorts ke liye \"short video\" zaroor mention karein",
                    "Script chahiye to bas \"script likho\" kahein"
                )
            else ->
                "💡 PK AI Tips" to listOf(
                    "\"play\" + gaane ka naam — song card mein play hoga",
                    "Schedule ya reminder ke liye bas likh dein",
                    "Koi bhi sawal Roman Urdu mein poochein"
                )
        }
    }
}
