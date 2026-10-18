package com.hy.autoswipe

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

/** 悬浮窗停靠和上滑坐标共用这一处尺寸，避免两套读取各自改掉。 */
fun Context.displaySize(): Pair<Int, Int> {
    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = wm.currentWindowMetrics.bounds
        bounds.width() to bounds.height()
    } else {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        metrics.widthPixels to metrics.heightPixels
    }
}
