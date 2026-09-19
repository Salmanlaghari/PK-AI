package com.salmanlaghari.pkai

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.salmanlaghari.pkai.ads.AdManager
import com.salmanlaghari.pkai.util.CrashDiagnosticsManager
import com.salmanlaghari.pkai.util.CrashHandler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PkAiApplication : Application(), Application.ActivityLifecycleCallbacks {

    private var currentActivity: Activity? = null

    override fun onCreate() {
        super.onCreate()

        // Initialize AdMob SDK
        AdManager.initialize(this)

        // Pre-load ads
        AdManager.loadAppOpenAd(this)
        AdManager.loadRewarded(this)

        registerActivityLifecycleCallbacks(this)

        // Install global crash handler after Hilt injection is complete.
        // Wrap in try/catch so a failure here never prevents the app from starting.
        try {
            if (::crashDiagnosticsManager.isInitialized) {
                CrashHandler.initialize(crashDiagnosticsManager)
            } else {
                android.util.Log.w("PkAiApplication", "crashDiagnosticsManager not initialized; skipping CrashHandler setup")
            }
        } catch (e: Throwable) {
            android.util.Log.e("PkAiApplication", "Failed to install CrashHandler", e)
        }
    }

    // ========================
    // APP OPEN AD — Show on app foreground
    // ========================

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
        // Show App Open Ad when app comes to the foreground
        AdManager.showAppOpenAdIfAvailable(activity)
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) currentActivity = null
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    @Inject
    lateinit var crashDiagnosticsManager: CrashDiagnosticsManager
}
