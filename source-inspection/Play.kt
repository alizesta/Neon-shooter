package com.claude.neonshooter

import android.app.Activity
import com.google.android.gms.games.PlayGames
import android.widget.Toast

/** Google Play Games Services: sign-in, leaderboard, achievements. All calls are failure-safe. */
object Play {
    @Volatile var signedIn = false

    fun check(a: Activity) {
        try {
            PlayGames.getGamesSignInClient(a).isAuthenticated.addOnCompleteListener { t ->
                signedIn = t.isSuccessful && t.result.isAuthenticated
            }
        } catch (e: Throwable) { signedIn = false }
    }

    fun signIn(a: Activity) {
        try {
            PlayGames.getGamesSignInClient(a).signIn().addOnCompleteListener { t ->
                signedIn = t.isSuccessful && t.result.isAuthenticated
                val message = if (signedIn) "Google Play Games connected" else "Google Play Games sign-in failed. Check Play Console setup."
                Toast.makeText(a, message, Toast.LENGTH_LONG).show()
            }
        } catch (e: Throwable) {
            signedIn = false
            Toast.makeText(a, "Sign-in error: " + (e.message ?: "unknown"), Toast.LENGTH_LONG).show()
        }
    }

    fun submit(a: Activity, score: Int) {
        if (!signedIn) return
        try {
            PlayGames.getLeaderboardsClient(a).submitScore(a.getString(R.string.leaderboard_high_score), score.toLong())
        } catch (e: Throwable) {}
    }

    fun unlock(a: Activity, resId: Int) {
        if (!signedIn) return
        try { PlayGames.getAchievementsClient(a).unlock(a.getString(resId)) } catch (e: Throwable) {}
    }

    fun showLeaderboard(a: Activity) {
        if (!signedIn) { signIn(a); return }
        try {
            PlayGames.getLeaderboardsClient(a).getLeaderboardIntent(a.getString(R.string.leaderboard_high_score))
                .addOnSuccessListener { a.startActivityForResult(it, 9001) }
        } catch (e: Throwable) {}
    }

    fun showAchievements(a: Activity) {
        if (!signedIn) { signIn(a); return }
        try {
            PlayGames.getAchievementsClient(a).achievementsIntent
                .addOnSuccessListener { a.startActivityForResult(it, 9002) }
        } catch (e: Throwable) {}
    }
}
