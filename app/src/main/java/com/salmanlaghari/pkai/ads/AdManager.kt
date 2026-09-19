package com.salmanlaghari.pkai.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * PK AI — Centralized AdMob Manager
 * Manages all ad types: Banner, Interstitial, Rewarded, App Open
 */
object AdManager {

    private const val TAG = "PKAI_Ads"

    // Test Ad Unit IDs (Official Google AdMob Sample Test IDs)
    const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_APP_OPEN_ID = "ca-app-pub-3940256099942544/9257395921"

    // Production Ad Unit IDs
    const val PROD_BANNER_HOME_ID = "ca-app-pub-8178045957849630/8608945264"
    const val PROD_BANNER_TOOLS_ID = "ca-app-pub-8178045957849630/6736626241"
    const val PROD_REWARDED_UNLOCK_ID = "ca-app-pub-8178045957849630/6912720217"
    const val PROD_APP_OPEN_ID = "ca-app-pub-8178045957849630/1244626412"

    fun isEmulator(): Boolean {
        val brand = android.os.Build.BRAND.lowercase()
        val device = android.os.Build.DEVICE.lowercase()
        val fingerprint = android.os.Build.FINGERPRINT.lowercase()
        val hardware = android.os.Build.HARDWARE.lowercase()
        val model = android.os.Build.MODEL.lowercase()
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val product = android.os.Build.PRODUCT.lowercase()
        val board = android.os.Build.BOARD.lowercase()

        return fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || model.contains("google_sdk")
                || model.contains("emulator")
                || model.contains("android sdk built for")
                || model.contains("sdk")
                || hardware.contains("goldfish")
                || hardware.contains("ranchu")
                || manufacturer.contains("genymotion")
                || brand.contains("generic")
                || device.contains("generic")
                || product.contains("sdk")
                || product.contains("emulator")
                || product.contains("simulator")
                || board.contains("goldfish")
                || board.contains("ranchu")
    }

    val isTestMode: Boolean
        get() = com.salmanlaghari.pkai.BuildConfig.DEBUG || isEmulator()

    val BANNER_HOME_ID: String
        get() = if (isTestMode) TEST_BANNER_ID else PROD_BANNER_HOME_ID

    val BANNER_TOOLS_ID: String
        get() = if (isTestMode) TEST_BANNER_ID else PROD_BANNER_TOOLS_ID

    val REWARDED_UNLOCK_ID: String
        get() = if (isTestMode) TEST_REWARDED_ID else PROD_REWARDED_UNLOCK_ID

    val APP_OPEN_ID: String
        get() = if (isTestMode) TEST_APP_OPEN_ID else PROD_APP_OPEN_ID

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var appOpenAd: AppOpenAd? = null
    private var isAppOpenAdShowing = false
    private var lastAppOpenShowTime = 0L
    private const val MIN_APP_OPEN_INTERVAL_MS = 4 * 60 * 60 * 1000L // 4 hours

    // ========================
    // INITIALIZATION
    // ========================

    fun initialize(context: Context) {
        try {
            val requestConfiguration = MobileAds.getRequestConfiguration()
                .toBuilder()
                .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                .build()
            MobileAds.setRequestConfiguration(requestConfiguration)

            MobileAds.initialize(context) { initializationStatus ->
                Log.d(TAG, "AdMob initialized: ${initializationStatus.adapterStatusMap}")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "AdMob initialization warning", e)
        }
    }

    // ========================
    // BANNER ADS
    // ========================

    fun createBannerAdView(context: Context, adUnitId: String): AdView {
        return AdView(context).apply {
            setAdSize(AdSize.BANNER)
            this.adUnitId = adUnitId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d(TAG, "Banner loaded: $adUnitId")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Banner failed: $adUnitId — ${error.message}")
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    // ========================
    // INTERSTITIAL ADS
    // ========================

    fun loadInterstitial(context: Context, adUnitId: String) {
        InterstitialAd.load(context, adUnitId, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d(TAG, "Interstitial loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.e(TAG, "Interstitial failed: ${error.message}")
                }
            })
    }

    fun showInterstitial(activity: Activity, onDismissed: (() -> Unit)? = null) {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    onDismissed?.invoke()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    interstitialAd = null
                    onDismissed?.invoke()
                }
            }
            ad.show(activity)
        } else {
            Log.d(TAG, "Interstitial not ready")
            onDismissed?.invoke()
        }
    }

    fun isInterstitialReady(): Boolean = interstitialAd != null

    // ========================
    // REWARDED ADS
    // ========================

    fun loadRewarded(context: Context) {
        if (isTestMode) {
            Log.d(TAG, "Skipping Rewarded ad video loading in test/emulator mode to avoid Codec2 bufferpool and player errors")
            return
        }
        try {
            RewardedAd.load(context, REWARDED_UNLOCK_ID, AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        Log.d(TAG, "Rewarded loaded")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null
                        Log.e(TAG, "Rewarded failed: ${error.message}")
                    }
                })
        } catch (e: Throwable) {
            Log.w(TAG, "Error loading rewarded ad", e)
        }
    }

    fun showRewarded(activity: Activity, onRewarded: (() -> Unit)? = null, onDismissed: (() -> Unit)? = null) {
        if (isTestMode) {
            Log.d(TAG, "Test/emulator mode: auto-granting reward")
            onRewarded?.invoke()
            onDismissed?.invoke()
            return
        }
        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    onDismissed?.invoke()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    rewardedAd = null
                    onDismissed?.invoke()
                }
            }
            ad.show(activity) {
                Log.d(TAG, "User rewarded!")
                onRewarded?.invoke()
            }
        } else {
            Log.d(TAG, "Rewarded not ready")
            onDismissed?.invoke()
        }
    }

    fun isRewardedReady(): Boolean = if (isTestMode) true else rewardedAd != null

    // ========================
    // APP OPEN AD
    // ========================

    fun loadAppOpenAd(context: Context) {
        if (isTestMode) {
            Log.d(TAG, "Skipping App Open ad in test/emulator mode to avoid video decoding errors")
            return
        }
        try {
            AppOpenAd.load(context, APP_OPEN_ID, AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        appOpenAd = ad
                        Log.d(TAG, "App Open loaded")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        appOpenAd = null
                        Log.e(TAG, "App Open failed: ${error.message}")
                    }
                })
        } catch (e: Throwable) {
            Log.w(TAG, "Error loading App Open ad", e)
        }
    }

    fun showAppOpenAdIfAvailable(activity: Activity) {
        if (isTestMode || isAppOpenAdShowing) return
        val currentTime = System.currentTimeMillis()
        if (lastAppOpenShowTime > 0 && (currentTime - lastAppOpenShowTime) < MIN_APP_OPEN_INTERVAL_MS) {
            return
        }
        val ad = appOpenAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    isAppOpenAdShowing = false
                    lastAppOpenShowTime = System.currentTimeMillis()
                    loadAppOpenAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    appOpenAd = null
                    isAppOpenAdShowing = false
                }

                override fun onAdShowedFullScreenContent() {
                    isAppOpenAdShowing = true
                    lastAppOpenShowTime = System.currentTimeMillis()
                }
            }
            ad.show(activity)
        }
    }
}
