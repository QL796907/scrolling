package com.hy.autoswipe

import android.app.Activity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/** 固定浅色状态栏。厂商 ROM 上个别窗口 API 会抛异常，全部包住以免闪退。 */
object SystemBars {
    fun lockLight(activity: Activity) {
        val window = activity.window
        try {
            WindowCompat.setDecorFitsSystemWindows(window, true)
        } catch (_: Exception) {
        }
        try {
            val bg = ContextCompat.getColor(activity, R.color.bg)
            window.statusBarColor = bg
            window.navigationBarColor = bg
        } catch (_: Exception) {
        }
        try {
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        } catch (_: Exception) {
        }
    }
}
