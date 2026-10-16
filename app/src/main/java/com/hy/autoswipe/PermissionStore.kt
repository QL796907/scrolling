package com.hy.autoswipe

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

object PermissionStore {
    private const val PREFS = "permissions"
    private const val KEY_NOTIF_ASKED = "notification_asked"

    fun shouldAskNotification(context: Context): Boolean =
        !context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIF_ASKED, false)

    fun markNotificationAsked(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIF_ASKED, true)
            .apply()
    }

    fun waitForAccessibility(timeoutMs: Long, callback: (Boolean) -> Unit): () -> Unit {
        if (SwipeAccessibilityService.instance != null) {
            callback(true)
            return {}
        }
        val handler = Handler(Looper.getMainLooper())
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var cancelled = false
        val check = object : Runnable {
            override fun run() {
                if (cancelled) return
                when {
                    SwipeAccessibilityService.instance != null -> callback(true)
                    SystemClock.uptimeMillis() >= deadline -> callback(false)
                    else -> handler.postDelayed(this, 120)
                }
            }
        }
        handler.post(check)
        return {
            cancelled = true
            handler.removeCallbacks(check)
        }
    }
}
