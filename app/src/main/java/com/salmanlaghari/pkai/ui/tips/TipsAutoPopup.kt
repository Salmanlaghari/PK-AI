package com.salmanlaghari.pkai.ui.tips

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shows the Tips bottom sheet automatically the first time a chat screen is
 * entered. Shown at most once per screen per app session, and never again
 * after the user ticks "Don't show automatically again" (persisted in
 * [PreferencesManager.tipsDontShowAgain]).
 */
object TipsAutoPopup {

    private const val TAG = "TipsAutoPopup"

    /** Screens that already showed the popup in this process. */
    private val shownScreens = mutableSetOf<String>()

    fun maybeShow(
        fragment: Fragment,
        preferencesManager: PreferencesManager,
        screen: String
    ) {
        if (screen in shownScreens) return
        // viewLifecycleOwner scope: the coroutine dies with the view, so the
        // sheet can never pop over a different screen after navigation.
        val viewLifecycleOwner = try {
            fragment.viewLifecycleOwner
        } catch (_: IllegalStateException) {
            return // view not created yet
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val dontShow = try {
                preferencesManager.tipsDontShowAgain.first()
            } catch (e: CancellationException) {
                throw e // never swallow cancellation
            } catch (_: Exception) {
                false
            }
            if (dontShow) return@launch
            // Re-check: the fragment may have navigated away while reading prefs.
            if (!fragment.isAdded || fragment.view == null) return@launch
            try {
                fragment.findNavController().navigate(R.id.tipsFragment)
                // Mark shown only after the popup actually displayed, so a
                // transient failure still retries on the next entry.
                shownScreens.add(screen)
            } catch (e: Exception) {
                Log.w(TAG, "Tips auto-popup navigation failed", e)
            }
        }
    }
}
