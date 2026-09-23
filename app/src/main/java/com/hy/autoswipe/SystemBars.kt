package com.hy.autoswipe

import android.app.Activity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/** 固定浅色状态栏，避免和悬浮窗/权限弹窗抢绘制时闪烁。 */
object SystemBars {
    fun lockLight(activity: Activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val bg = ContextCompat.getColor(activity, R.color.bg)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
}
