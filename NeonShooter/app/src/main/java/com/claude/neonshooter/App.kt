package com.claude.neonshooter

import android.app.Application
import com.google.android.gms.games.PlayGamesSdk

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        try { PlayGamesSdk.initialize(this) } catch (e: Throwable) {}
    }
}
