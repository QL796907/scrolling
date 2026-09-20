package com.hy.autoswipe

import android.app.Application
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        if (Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        }
    }
}
