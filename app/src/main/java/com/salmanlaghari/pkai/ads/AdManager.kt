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

    // Ad Unit IDs
    const val BANNER_HOME_ID = "ca-app-pub-8178045957849630/8608945264"
    const val BANNER_TOOLS_ID = "ca-app-pub-8178045957849630/6736626241"
    const val REWARDED_UNLOCK_ID = "ca-app-pub-8178045957849630/6912720217"
    const val APP_OPEN_ID = "ca-app-pub-8178045957849630/1244626412"

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var appOpenAd: AppOpenAd? = null
    private var isAppOpenAdShowing = false

    // ========================
    // INITIALIZATION
    // ========================

    fun initialize(context: Context) {
        MobileAds.initialize(context) { initializationStatus ->
            Log.d(TAG, "AdMob initialized: ${initializationStatus.adapterStatusMap}")
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
    }

    fun showRewarded(activity: Activity, onRewarded: (() -> Unit)? = null, onDismissed: (() -> Unit)? = null) {
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

    fun isRewardedReady(): Boolean = rewardedAd != null

    // ========================
    // APP OPEN AD
    // ========================

    fun loadAppOpenAd(context: Context) {
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
    }

    fun showAppOpenAdIfAvailable(activity: Activity) {
        if (isAppOpenAdShowing) return
        val ad = appOpenAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    isAppOpenAdShowing = false
                    loadAppOpenAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    appOpenAd = null
                    isAppOpenAdShowing = false
                }

                override fun onAdShowedFullScreenContent() {
                    isAppOpenAdShowing = true
                }
            }
            ad.show(activity)
        }
    }
}
