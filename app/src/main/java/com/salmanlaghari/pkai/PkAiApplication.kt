package com.salmanlaghari.pkai

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.salmanlaghari.pkai.ads.AdManager
import dagger.hilt.android.HiltAndroidApp

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
    }

    // ========================
    // APP OPEN AD — Show on app foreground
    // ========================

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
        // Show App Open Ad when app comes to foreground
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
}
