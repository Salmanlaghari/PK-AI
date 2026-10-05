package com.salmanlaghari.pkai.ui.home

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import com.salmanlaghari.pkai.R

/**
 * Contextual "Tips Highlights" popup.
 *
 * Shows a card at the top of the screen for ~5 seconds with 2-3 tips
 * RELATED to what the user just searched/asked (song search → music tips,
 * code question → coding tips, etc.). Slides down, auto-dismisses, and can
 * be tapped to dismiss early.
 *
 * The banner lives inside the caller's [ViewGroup] (typically the fragment's
 * root view) and is tied to its [Lifecycle]: when the lifecycle is destroyed
 * (fragment view teardown, rotation, navigation) the banner is removed and
 * its auto-dismiss cancelled, so it can never outlive the screen or leak a
 * destroyed Activity's view tree. Example:
 *
 *     TipsHighlightBanner.show(binding.root, viewLifecycleOwner.lifecycle, userQuery)
 *
 * Safe to call from any thread.
 */
object TipsHighlightBanner {

    private const val TAG = "tips_banner_highlight"
    private const val DISPLAY_MS = 5000L
    private const val ANIM_MS = 280L

    private val mainHandler = Handler(Looper.getMainLooper())
    /** Auto-dismiss runnables keyed by container, so each view gets its own. */
    private val pendingDismiss = mutableMapOf<ViewGroup, Runnable>()
    /** Lifecycle observers keyed by banner view, so teardown removes them. */
    private val bannerObservers = mutableMapOf<View, Pair<Lifecycle, LifecycleObserver>>()

    /**
     * Shows the tips banner for [query] inside [container].
     * Any banner already showing in the container is replaced.
     */
    fun show(container: ViewGroup, lifecycle: Lifecycle, query: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(container, lifecycle, query) }
            return
        }
        // One banner at a time per container — drop the previous one without animation.
        dismissInternal(container, animate = false)

        val (title, tips) = tipsFor(query)
        val banner = LayoutInflater.from(container.context)
            .inflate(R.layout.banner_tips_highlight, container, false)
        banner.tag = TAG
        banner.findViewById<TextView>(R.id.tvTipsTitle).text = title
        val tipContainer = banner.findViewById<LinearLayout>(R.id.llTipsContainer)
        val density = container.resources.displayMetrics.density
        tips.take(3).forEach { tip ->
            tipContainer.addView(TextView(container.context).apply {
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
        container.addView(banner, params)

        // Slide down from the top once measured.
        banner.post {
            banner.translationY = -banner.height.toFloat()
            banner.animate()
                .translationY(0f)
                .setDuration(ANIM_MS)
                .start()
        }
        // Tap to dismiss early.
        banner.setOnClickListener { dismissInternal(container, animate = true) }

        // Auto-dismiss after 5 seconds, tracked per container.
        val dismiss = Runnable { dismissInternal(container, animate = true) }
        pendingDismiss[container] = dismiss
        mainHandler.postDelayed(dismiss, DISPLAY_MS)

        // Tie teardown to the host lifecycle (fragment view, activity, …):
        // ON_DESTROY removes the banner and cancels its auto-dismiss.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) dismissInternal(container, animate = false)
        }
        bannerObservers[banner] = lifecycle to observer
        lifecycle.addObserver(observer)
    }

    /** Removes this container's banner immediately, if present. */
    fun dismiss(container: ViewGroup) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismiss(container) }
            return
        }
        dismissInternal(container, animate = false)
    }

    /** Removes the banner if present, sliding it back up when [animate]. */
    private fun dismissInternal(root: ViewGroup, animate: Boolean) {
        pendingDismiss.remove(root)?.let { mainHandler.removeCallbacks(it) }
        val banner = root.findViewWithTag<View>(TAG) ?: return
        bannerObservers.remove(banner)?.let { (lifecycle, observer) ->
            lifecycle.removeObserver(observer)
        }
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
        // Word-boundary matching: "api" must not match inside "captain".
        fun has(vararg words: String) = words.any { w ->
            Regex("\\b${Regex.escape(w)}\\b").containsMatchIn(q)
        }
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
