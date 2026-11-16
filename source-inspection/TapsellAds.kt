package com.claude.neonshooter

import android.app.Activity
import android.content.Context
import android.util.Log
import ir.tapsell.mediation.Tapsell
import ir.tapsell.mediation.ad.AdStateListener
import ir.tapsell.mediation.ad.request.RequestResultListener
import ir.tapsell.mediation.ad.show.AdShowCompletionState
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tapsell Mediation adapter.
 *
 * Reward coins are granted only from onRewarded(). Rewarded and interstitial
 * responses are preloaded in the background and cached until the game needs them.
 */
object TapsellAds {
    private const val TAG = "NeonShooterAds"

    @Volatile private var hookInstalled = false
    @Volatile private var initialized = false
    @Volatile private var appContext: Context? = null

    private val rewardedCache = ConcurrentLinkedQueue<String>()
    private val interstitialCache = ConcurrentLinkedQueue<String>()
    private val rewardedPreloadInFlight = AtomicBoolean(false)
    private val interstitialPreloadInFlight = AtomicBoolean(false)

    fun initialize(activity: Activity) {
        appContext = activity.applicationContext
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
                        Log.w(TAG, "Could not apply default non-personalized consent", error)
                    }
                    Log.i(TAG, "Tapsell initialized")
                    preloadAll()
                }
            } catch (error: Throwable) {
                hookInstalled = false
                Log.e(TAG, "Tapsell initialization listener failed", error)
            }
        }
    }

    /** Load one response for each enabled placement without blocking gameplay. */
    fun preloadAll() {
        val context = appContext ?: return
        if (!initialized) return
        preloadRewarded(context)
        preloadInterstitial(context)
    }

    private fun preloadRewarded(context: Context) {
        if (rewardedCache.isNotEmpty() || !rewardedPreloadInFlight.compareAndSet(false, true)) return
        val zone = zone(context, R.string.tapsell_rewarded_zone_id)
        if (zone.isNullOrBlank()) {
            rewardedPreloadInFlight.set(false)
            return
        }

        try {
            Tapsell.requestRewardedAd(zone, object : RequestResultListener {
                override fun onSuccess(adId: String) {
                    if (adId.isNotBlank()) rewardedCache.offer(adId)
                    rewardedPreloadInFlight.set(false)
                }

                override fun onFailure(message: String) {
                    rewardedPreloadInFlight.set(false)
                    Log.d(TAG, "Rewarded preload unavailable: " + message)
                }
            })
        } catch (error: Throwable) {
            rewardedPreloadInFlight.set(false)
            Log.w(TAG, "Rewarded preload request failed", error)
        }
    }

    private fun preloadInterstitial(context: Context) {
        if (interstitialCache.isNotEmpty() || !interstitialPreloadInFlight.compareAndSet(false, true)) return
        val zone = zone(context, R.string.tapsell_interstitial_zone_id)
        if (zone.isNullOrBlank()) {
            interstitialPreloadInFlight.set(false)
            return
        }

        try {
            Tapsell.requestInterstitialAd(zone, object : RequestResultListener {
                override fun onSuccess(adId: String) {
                    if (adId.isNotBlank()) interstitialCache.offer(adId)
                    interstitialPreloadInFlight.set(false)
                }

                override fun onFailure(message: String) {
                    interstitialPreloadInFlight.set(false)
                    Log.d(TAG, "Interstitial preload unavailable: " + message)
                }
            })
        } catch (error: Throwable) {
            interstitialPreloadInFlight.set(false)
            Log.w(TAG, "Interstitial preload request failed", error)
        }
    }

    fun showRewarded(activity: Activity, callback: (Boolean) -> Unit) {
        if (!ready(activity)) {
            deliver(activity, AtomicBoolean(false), callback, false)
            return
        }

        val cachedId = rewardedCache.poll()
        if (!cachedId.isNullOrBlank()) {
            showRewardedId(activity, cachedId, callback)
            return
        }

        val zone = zone(activity, R.string.tapsell_rewarded_zone_id)
        if (zone.isNullOrBlank()) {
            deliver(activity, AtomicBoolean(false), callback, false)
            return
        }

        val delivered = AtomicBoolean(false)
        try {
            Tapsell.requestRewardedAd(zone, object : RequestResultListener {
                override fun onSuccess(adId: String) {
                    if (adId.isBlank()) {
                        deliver(activity, delivered, callback, false)
                    } else {
                        showRewardedId(activity, adId) { result ->
                            if (delivered.compareAndSet(false, true)) callback(result)
                        }
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

    private fun showRewardedId(activity: Activity, adId: String, callback: (Boolean) -> Unit) {
        val delivered = AtomicBoolean(false)
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
                    preloadAll()
                }

                override fun onAdFailed(message: String) {
                    Log.e(TAG, "Rewarded show failed: " + message)
                    deliver(activity, delivered, callback, false)
                    preloadAll()
                }
            })
        } catch (error: Throwable) {
            Log.e(TAG, "Rewarded display threw", error)
            deliver(activity, delivered, callback, false)
            preloadAll()
        }
    }

    fun showInterstitial(activity: Activity, callback: () -> Unit) {
        if (!ready(activity)) {
            finishInterstitial(activity, AtomicBoolean(false), callback)
            return
        }

        val cachedId = interstitialCache.poll()
        if (!cachedId.isNullOrBlank()) {
            showInterstitialId(activity, cachedId, callback)
            return
        }

        val zone = zone(activity, R.string.tapsell_interstitial_zone_id)
        if (zone.isNullOrBlank()) {
            finishInterstitial(activity, AtomicBoolean(false), callback)
            return
        }

        val completed = AtomicBoolean(false)
        try {
            Tapsell.requestInterstitialAd(zone, object : RequestResultListener {
                override fun onSuccess(adId: String) {
                    if (adId.isBlank()) {
                        finishInterstitial(activity, completed, callback)
                    } else {
                        showInterstitialId(activity, adId) {
                            if (completed.compareAndSet(false, true)) callback()
                        }
                    }
                }

                override fun onFailure(message: String) {
                    Log.w(TAG, "Interstitial request failed: " + message)
                    finishInterstitial(activity, completed, callback)
                }
            })
        } catch (error: Throwable) {
            Log.e(TAG, "Interstitial request threw", error)
            finishInterstitial(activity, completed, callback)
        }
    }

    private fun showInterstitialId(activity: Activity, adId: String, callback: () -> Unit) {
        val completed = AtomicBoolean(false)
        try {
            Tapsell.showInterstitialAd(adId, activity, object : AdStateListener.Interstitial {
                override fun onAdClicked() { Log.d(TAG, "Interstitial clicked") }
                override fun onAdImpression() { Log.d(TAG, "Interstitial impression") }

                override fun onAdClosed(completionState: AdShowCompletionState) {
                    Log.d(TAG, "Interstitial closed: " + completionState.name)
                    finishInterstitial(activity, completed, callback)
                    preloadAll()
                }

                override fun onAdFailed(message: String) {
                    Log.w(TAG, "Interstitial show failed: " + message)
                    finishInterstitial(activity, completed, callback)
                    preloadAll()
                }
            })
        } catch (error: Throwable) {
            Log.e(TAG, "Interstitial display threw", error)
            finishInterstitial(activity, completed, callback)
            preloadAll()
        }
    }

    private fun zone(context: Context, resourceId: Int): String? = try {
        context.getString(resourceId).trim()
    } catch (error: Throwable) {
        Log.e(TAG, "Ad zone resource missing", error)
        null
    }

    private fun ready(activity: Activity): Boolean =
        initialized && !activity.isFinishing && !activity.isDestroyed

    private fun deliver(
        activity: Activity,
        once: AtomicBoolean,
        callback: (Boolean) -> Unit,
        rewarded: Boolean
    ) {
        if (!once.compareAndSet(false, true)) return
        try {
            if (!activity.isFinishing && !activity.isDestroyed) {
                activity.runOnUiThread {
                    callback(rewarded)
                    preloadAll()
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Reward callback delivery failed", error)
        }
    }

    private fun finishInterstitial(activity: Activity, once: AtomicBoolean, callback: () -> Unit) {
        if (!once.compareAndSet(false, true)) return
        try {
            if (!activity.isFinishing && !activity.isDestroyed) {
                activity.runOnUiThread {
                    callback()
                    preloadAll()
                }
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Interstitial completion callback failed", error)
        }
    }
}
