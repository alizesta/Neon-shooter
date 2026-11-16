package com.claude.neonshooter

import android.app.Activity
import android.util.Log
import ir.tapsell.mediation.Tapsell
import ir.tapsell.mediation.ad.AdStateListener
import ir.tapsell.mediation.ad.request.RequestResultListener
import ir.tapsell.mediation.ad.show.AdShowCompletionState
import java.util.concurrent.atomic.AtomicBoolean

object TapsellAds {
    private const val TAG = "NeonShooterAds"
    @Volatile private var hookInstalled = false
    @Volatile private var initialized = false

    fun initialize(activity: Activity) {
        if (hookInstalled) return
        synchronized(this) {
            if (hookInstalled) return
            hookInstalled = true
            try {
                Tapsell.setInitializationListener {
                    initialized = true
                    try {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            Tapsell.setUserConsent(activity, false)
                        }
                    } catch (error: Throwable) {
                        Log.w(TAG, "Default non-personalized consent could not be applied", error)
                    }
                    Log.i(TAG, "Tapsell initialized")
                }
            } catch (error: Throwable) {
                hookInstalled = false
                Log.e(TAG, "Tapsell init listener failed", error)
            }
        }
    }

    fun showRewarded(activity: Activity, callback: (Boolean) -> Unit) {
        val zoneId = readZone(activity, R.string.tapsell_rewarded_zone_id)
        if (zoneId.isNullOrBlank() || !ready(activity)) {
            deliver(activity, AtomicBoolean(false), callback, false)
            return
        }
        val delivered = AtomicBoolean(false)
        try {
            Tapsell.requestRewardedAd(zoneId, object : RequestResultListener {
                override fun onSuccess(adId: String) {
                    if (adId.isBlank()) {
                        deliver(activity, delivered, callback, false)
                        return
                    }
                    try {
                        Tapsell.showRewardedAd(adId, activity, object : AdStateListener.Rewarded {
                            private val earned = AtomicBoolean(false)
                            override fun onAdClicked() { Log.d(TAG, "Rewarded clicked") }
                            override fun onAdImpression() { Log.d(TAG, "Rewarded impression") }
                            override fun onRewarded() {
                                earned.set(true)
                                deliver(activity, delivered, callback, true)
                            }
                            override fun onAdClosed(completionState: AdShowCompletionState) {
                                Log.d(TAG, "Rewarded closed: " + completionState.name)
                                if (!earned.get()) deliver(activity, delivered, callback, false)
                            }
                            override fun onAdFailed(message: String) {
                                Log.e(TAG, "Rewarded show failed: " + message)
                                deliver(activity, delivered, callback, false)
                            }
                        })
                    } catch (error: Throwable) {
                        Log.e(TAG, "Rewarded display threw", error)
                        deliver(activity, delivered, callback, false)
                    }
                }
                override fun onFailure(message: String) {
                    Log.w(TAG, "Rewarded request failed: " + message)
                    deliver(activity, delivered, callback, false)
                }
            })
        } catch (error: Throwable) {
            Log.e(TAG, "Rewarded request threw", error)
            deliver(activity, delivered, callback, false)
        }
    }

    fun showInterstitial(activity: Activity, callback: () -> Unit) {
        val zoneId = readZone(activity, R.string.tapsell_interstitial_zone_id)
        if (zoneId.isNullOrBlank() || !ready(activity)) {
            finishInterstitial(activity, AtomicBoolean(false), callback)
            return
        }
        val finished = AtomicBoolean(false)
        try {
            Tapsell.requestInterstitialAd(zoneId, object : RequestResultListener {
                override fun onSuccess(adId: String) {
                    if (adId.isBlank()) {
                        finishInterstitial(activity, finished, callback)
                        return
                    }
                    try {
                        Tapsell.showInterstitialAd(adId, activity, object : AdStateListener.Interstitial {
                            override fun onAdClicked() { Log.d(TAG, "Interstitial clicked") }
                            override fun onAdImpression() { Log.d(TAG, "Interstitial impression") }
                            override fun onAdClosed(completionState: AdShowCompletionState) {
                                Log.d(TAG, "Interstitial closed: " + completionState.name)
                                finishInterstitial(activity, finished, callback)
                            }
                            override fun onAdFailed(message: String) {
                                Log.w(TAG, "Interstitial show failed: " + message)
                                finishInterstitial(activity, finished, callback)
                            }
                        })
                    } catch (error: Throwable) {
                        Log.e(TAG, "Interstitial display threw", error)
                        finishInterstitial(activity, finished, callback)
                    }
                }
                override fun onFailure(message: String) {
                    Log.w(TAG, "Interstitial request failed: " + message)
                    finishInterstitial(activity, finished, callback)
                }
            })
        } catch (error: Throwable) {
            Log.e(TAG, "Interstitial request threw", error)
            finishInterstitial(activity, finished, callback)
        }
    }

    private fun readZone(activity: Activity, resourceId: Int): String? = try {
        activity.getString(resourceId).trim()
    } catch (error: Throwable) {
        Log.e(TAG, "Ad zone resource missing", error)
        null
    }

    private fun ready(activity: Activity): Boolean =
        initialized && !activity.isFinishing && !activity.isDestroyed

    private fun deliver(activity: Activity, once: AtomicBoolean, callback: (Boolean) -> Unit, rewarded: Boolean) {
        if (!once.compareAndSet(false, true)) return
        try {
            if (!activity.isFinishing && !activity.isDestroyed) activity.runOnUiThread { callback(rewarded) }
        } catch (error: Throwable) {
            Log.e(TAG, "Reward callback delivery failed", error)
        }
    }

    private fun finishInterstitial(activity: Activity, once: AtomicBoolean, callback: () -> Unit) {
        if (!once.compareAndSet(false, true)) return
        try {
            if (!activity.isFinishing && !activity.isDestroyed) activity.runOnUiThread(callback)
        } catch (error: Throwable) {
            Log.e(TAG, "Interstitial callback delivery failed", error)
        }
    }
}
