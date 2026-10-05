package com.salmanlaghari.pkai.ui.home

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import com.salmanlaghari.pkai.R
import java.lang.ref.WeakReference
import java.util.WeakHashMap

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
    /**
     * Auto-dismiss runnables keyed WEAKLY by container: a static map must
     * never pin a host view tree for the life of the process. The Runnable
     * itself holds only a [WeakReference] to the container (a strong capture
     * in the lambda would defeat the weak key), so a banner removed without
     * going through [dismissInternal] can't leak.
     */
    private val pendingDismiss = WeakHashMap<ViewGroup, Runnable>()
    /**
     * Lifecycle observers keyed WEAKLY by banner view. The value holds the
     * host Lifecycle (which doesn't reference the view), so entries vanish
     * with the banner view instead of pinning it.
     */
    private val bannerObservers = WeakHashMap<View, Pair<Lifecycle, LifecycleObserver>>()

    /**
     * Category matchers, compiled ONCE. Building a Regex per word per send
     * (~20 compilations on the main thread per message) was wasteful; these
     * cover the same word sets as [tipsFor] used to match inline.
     */
    private val MUSIC_PATTERN = Regex("\\b(song|gana|gaana|music|singer|sunao|suno)\\b")
    private val CODE_PATTERN =
        Regex("\\b(code|python|kotlin|java|function|error|bug|api)\\b")
    private val IMAGE_PATTERN =
        Regex("\\b(image|photo|tasveer|picture|draw|banao)\\b")
    private val VIDEO_PATTERN = Regex("\\b(video)\\b")

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
        // The banner itself is deliberately NOT clickable: for DISPLAY_MS it
        // sits on top of the fragment root, and a clickable banner would
        // swallow every tap on the header row (Premium/Free tabs, menu…).
        // Touches pass through to the views underneath; only the explicit
        // close button consumes taps.
        banner.isClickable = false
        banner.isFocusable = false
        banner.findViewById<ImageView>(R.id.ivTipsClose).setOnClickListener {
            dismissInternal(container, animate = true)
        }

        // Auto-dismiss after 5 seconds, tracked per container. The Runnable
        // captures only a WeakReference — a strong capture would pin the
        // container through the static map even with a weak key.
        val containerRef = WeakReference(container)
        val dismiss = Runnable { containerRef.get()?.let { dismissInternal(it, animate = true) } }
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
        // Word-boundary matching via the pre-compiled category patterns above:
        // "api" must not match inside "captain".
        return when {
            MUSIC_PATTERN.containsMatchIn(q) ->
                "🎵 Music Tips" to listOf(
                    "Gaane ka naam + singer likhein — best results milenge",
                    "Card par ▶ dabayein, song direct chat mein play hoga",
                    "\"play kesariya\" jaisa short command bhi kaam karta hai"
                )
            CODE_PATTERN.containsMatchIn(q) ->
                "💻 Coding Tips" to listOf(
                    "Error ka poora message paste karein — fix tez milega",
                    "Language ka naam zaroor likhein (Kotlin, Python…)",
                    "``` wale code blocks par Run dabakar test kar sakte hain"
                )
            IMAGE_PATTERN.containsMatchIn(q) ->
                "🎨 Image Tips" to listOf(
                    "Detail mein describe karein — style, colors, mood",
                    "Generated image par tap karke fullscreen dekhein",
                    "Roman Urdu ya English — dono mein prompt chalega"
                )
            VIDEO_PATTERN.containsMatchIn(q) ->
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
