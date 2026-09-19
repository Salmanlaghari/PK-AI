package com.salmanlaghari.pkai

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.salmanlaghari.pkai.ads.AdManager
import com.salmanlaghari.pkai.util.CrashHandler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PkAiApplication : Application(), Application.ActivityLifecycleCallbacks {

    private var currentActivity: Activity? = null

    override fun onCreate() {
        super.onCreate()

        // Pre-create Chromium WebView Code Cache directories to prevent simple_file_enumerator crashes
        try {
            val webViewCacheDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache")
            java.io.File(webViewCacheDir, "js").mkdirs()
            java.io.File(webViewCacheDir, "wasm").mkdirs()
        } catch (e: Throwable) {
            Log.w("PkAiApplication", "Failed to pre-create WebView cache directories", e)
        }

        // Install global crash handler safely with application context
        try {
            CrashHandler.initialize(this)
        } catch (e: Throwable) {
            Log.e("PkAiApplication", "Failed to install CrashHandler", e)
        }

        // Initialize AdMob SDK safely
        try {
            AdManager.initialize(this)
            if (!AdManager.isTestMode) {
                AdManager.loadAppOpenAd(this)
                AdManager.loadRewarded(this)
            }
        } catch (e: Throwable) {
            Log.w("PkAiApplication", "Failed to initialize AdManager", e)
        }

        registerActivityLifecycleCallbacks(this)
    }

    // ========================
    // APP OPEN AD — Show on app foreground
    // ========================
    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity

        // Show App Open Ad when app comes to foreground safely
        if (!AdManager.isTestMode) {
            try {
                AdManager.showAppOpenAdIfAvailable(activity)
            } catch (e: Throwable) {
                Log.w("PkAiApplication", "Failed to show App Open Ad", e)
            }
        }
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
}
