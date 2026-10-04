package com.salmanlaghari.pkai.ui.tips

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.salmanlaghari.pkai.R
import com.salmanlaghari.pkai.data.local.datastore.PreferencesManager
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

    /** Screens that already triggered the popup in this process. */
    private val shownScreens = mutableSetOf<String>()

    fun maybeShow(
        fragment: Fragment,
        preferencesManager: PreferencesManager,
        screen: String
    ) {
        if (!shownScreens.add(screen)) return
        fragment.lifecycleScope.launch {
            val dontShow = runCatching {
                preferencesManager.tipsDontShowAgain.first()
            }.getOrDefault(false)
            if (!dontShow && fragment.isAdded) {
                runCatching {
                    fragment.findNavController().navigate(R.id.tipsFragment)
                }.onFailure {
                    Log.w(TAG, "Tips auto-popup navigation failed", it)
                }
            }
        }
    }
}
